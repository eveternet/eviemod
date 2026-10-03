// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.shader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Small, token-based adapter for explicit Minecraft shader interfaces, not fixed-function OpenGL emulation. */
final class GlslCompatibility {
    private static final Set<Integer> DESKTOP_VERSIONS = Set.of(110, 120, 130, 140, 150, 330, 400, 410, 420, 430, 440, 450, 460);
    private static final Pattern VALUE_TYPE = Pattern.compile("(?:bool|int|uint|float|double|[biud]?vec[234]|d?mat[234](?:x[234])?)");
    private static final Set<String> PRECISION = Set.of("lowp", "mediump", "highp");
    private static final Map<String, String> TEXTURE_FUNCTIONS = Map.ofEntries(
            Map.entry("texture1D", "texture"), Map.entry("texture2D", "texture"),
            Map.entry("texture3D", "texture"), Map.entry("textureCube", "texture"),
            Map.entry("texture1DProj", "textureProj"), Map.entry("texture2DProj", "textureProj"),
            Map.entry("texture3DProj", "textureProj"), Map.entry("texture1DLod", "textureLod"),
            Map.entry("texture2DLod", "textureLod"), Map.entry("texture3DLod", "textureLod"),
            Map.entry("textureCubeLod", "textureLod"), Map.entry("texture1DProjLod", "textureProjLod"),
            Map.entry("texture2DProjLod", "textureProjLod"), Map.entry("texture3DProjLod", "textureProjLod"));

    private GlslCompatibility() {}

    record Token(String text, int start, int end, boolean directive) {}
    private record Edit(int start, int end, String text) {}

    /** Upgrade the dialect before shaderc preprocessing, retaining the shader's __VERSION__ decisions. */
    static String prepareVersion(String source) throws ShaderTranslator.TranslationException {
        List<Token> tokens = tokens(source);
        int version = 110; // GLSL's desktop default when #version is absent.
        int versionStart = -1, versionEnd = -1;
        for (int i = 0; i + 2 < tokens.size(); i++) {
            if (!tokens.get(i).text.equals("#") || !tokens.get(i + 1).text.equals("version")) continue;
            if (versionStart >= 0 || i != 0) throw unsupported("#version must be the first directive and appear only once");
            try { version = Integer.parseInt(tokens.get(i + 2).text); }
            catch (NumberFormatException e) { throw unsupported("invalid GLSL version"); }
            versionStart = tokens.get(i).start;
            versionEnd = tokens.get(i + 2).end;
            if (i + 3 < tokens.size() && tokens.get(i + 3).directive
                    && source.substring(versionEnd, tokens.get(i + 3).start).indexOf('\n') < 0) {
                String profile = tokens.get(i + 3).text;
                if (!profile.equals("core")) throw unsupported("GLSL profile '" + profile + "' (only desktop core/unspecified profiles are supported)");
                if (version < 150) throw unsupported("core profile requires GLSL 150 or newer");
                versionEnd = tokens.get(i + 3).end;
            }
        }
        if (!DESKTOP_VERSIONS.contains(version)) throw unsupported("GLSL version " + version);
        if (version >= 330) return source;
        List<Edit> edits = new ArrayList<>();
        for (Token t : tokens) {
            if (t.text.equals("__VERSION__")) edits.add(new Edit(t.start, t.end, Integer.toString(version)));
            // Upgrading pre-profile GLSL would otherwise change #ifdef/defined decisions.
            if (version < 150 && (t.text.equals("GL_core_profile") || t.text.equals("GL_compatibility_profile"))) {
                throw unsupported("profile-dependent preprocessing in pre-150 GLSL");
            }
        }
        if (versionStart >= 0) edits.add(new Edit(versionStart, versionEnd, "#version 330 core"));
        String result = apply(source, edits);
        return versionStart >= 0 ? result : "#version 330 core\n#line 1\n" + result;
    }

    /** Input is macro-expanded: inactive declarations and macro-generated syntax must not affect rewriting. */
    static String normalize(String source, ShaderTranslator.Stage stage) throws ShaderTranslator.TranslationException {
        List<Token> tokens = tokens(source);
        List<Edit> edits = new ArrayList<>();
        String fragColor = uniqueName(tokens, "EvieMetalFragColor");
        boolean needsFragColor = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.directive) continue;
            String replacement = switch (t.text) {
                case "attribute" -> {
                    if (stage != ShaderTranslator.Stage.VERTEX) throw unsupported("attribute in a fragment shader");
                    yield "in";
                }
                case "varying" -> stage == ShaderTranslator.Stage.VERTEX ? "out" : "in";
                case "gl_FragColor" -> {
                    if (stage != ShaderTranslator.Stage.FRAGMENT) throw unsupported("gl_FragColor in a vertex shader");
                    needsFragColor = true;
                    yield fragColor;
                }
                default -> null;
            };
            if (t.text.equals("gl_FragData")) throw unsupported("gl_FragData (multiple/dynamic color outputs are not supported)");
            if (t.text.startsWith("shadow1D") || t.text.startsWith("shadow2D")) {
                throw unsupported("legacy shadow sampling; use modern texture sampling with explicit result conversion");
            }
            String texture = TEXTURE_FUNCTIONS.get(t.text);
            if (texture != null && i + 1 < tokens.size() && tokens.get(i + 1).text.equals("(")) {
                // User-defined overloads can have different semantics. Never rename those silently.
                if (i > 0 && VALUE_TYPE.matcher(tokens.get(i - 1).text).matches()) {
                    throw unsupported("user-defined legacy texture function " + t.text);
                }
                replacement = texture;
            }
            if (replacement != null) edits.add(new Edit(t.start, t.end, replacement));
        }
        String rewritten = apply(source, edits);
        if (needsFragColor) rewritten = insertHeader(rewritten, "layout(location = 0) out vec4 " + fragColor + ";\n");
        return gatherLooseUniforms(rewritten);
    }

    /** Only top-level value declarations move. Uniform-block members, samplers and following code stay intact. */
    static String gatherLooseUniforms(String source) throws ShaderTranslator.TranslationException {
        List<Token> tokens = tokens(source);
        List<Edit> edits = new ArrayList<>();
        StringBuilder members = new StringBuilder();
        int depth = 0, declarationStart = 0;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.directive) continue;
            while (declarationStart < tokens.size() && tokens.get(declarationStart).directive) declarationStart++;
            if (depth == 0 && t.text.equals("uniform")) {
                int type = i + 1;
                while (type < tokens.size() && PRECISION.contains(tokens.get(type).text)) type++;
                if (type < tokens.size() && VALUE_TYPE.matcher(tokens.get(type).text).matches()) {
                    int end = type + 1;
                    while (end < tokens.size() && !tokens.get(end).text.equals(";") && !tokens.get(end).text.equals("{")) end++;
                    if (end >= tokens.size() || !tokens.get(end).text.equals(";")) throw unsupported("unterminated loose uniform declaration");
                    if (declarationStart != i) throw unsupported("layout/other qualifiers on loose value uniforms");
                    for (int j = type + 1; j < end; j++) {
                        if (tokens.get(j).text.equals("=")) throw unsupported("loose uniform initializers");
                    }
                    members.append(source, t.end, tokens.get(end).end).append('\n');
                    // Blank in place so existing #line directives and following tokens retain their positions.
                    String blank = source.substring(t.start, tokens.get(end).end).replaceAll("[^\\r\\n]", " ");
                    edits.add(new Edit(t.start, tokens.get(end).end, blank));
                    i = end;
                    declarationStart = end + 1;
                    continue;
                }
            }
            if (t.text.equals("{")) depth++;
            if (t.text.equals("}")) depth--;
            if (depth == 0 && (t.text.equals(";") || t.text.equals("}"))) declarationStart = i + 1;
        }
        if (members.isEmpty()) return source;
        if (tokens.stream().anyMatch(t -> t.text.equals(ShaderTranslator.DEFAULT_BLOCK))) throw unsupported("reserved uniform block " + ShaderTranslator.DEFAULT_BLOCK);
        return insertHeader(apply(source, edits), "layout(std140) uniform " + ShaderTranslator.DEFAULT_BLOCK + " {\n" + members + "};\n");
    }

    /** Insert generated declarations after #version/#extension, before user code and its #line mapping. */
    private static String insertHeader(String source, String header) {
        int insert = 0;
        for (Token t : tokens(source)) {
            if (!t.directive) break;
            if (t.text.equals("version") || t.text.equals("extension")) {
                int newline = source.indexOf('\n', t.end);
                insert = newline < 0 ? source.length() : newline + 1;
            }
        }
        int line = 1;
        for (int i = 0; i < insert; i++) if (source.charAt(i) == '\n') line++;
        return source.substring(0, insert) + "\n" + header + "#line " + line + "\n" + source.substring(insert);
    }

    private static String uniqueName(List<Token> tokens, String base) {
        String name = base;
        Set<String> names = new java.util.HashSet<>();
        for (Token t : tokens) names.add(t.text);
        while (names.contains(name)) name += "_";
        return name;
    }

    private static String apply(String source, List<Edit> edits) {
        edits.sort(java.util.Comparator.comparingInt(Edit::start));
        StringBuilder result = new StringBuilder();
        int offset = 0;
        for (Edit e : edits) {
            result.append(source, offset, e.start).append(e.text);
            offset = e.end;
        }
        return result.append(source, offset, source.length()).toString();
    }

    /** Preserve offsets and distinguish directives, comments and identifier boundaries. */
    static List<Token> tokens(String source) {
        List<Token> result = new ArrayList<>();
        boolean directive = false, lineStart = true;
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                if (c == '\n') { directive = false; lineStart = true; }
                i++;
                continue;
            }
            if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i + 2);
                i = end < 0 ? source.length() : end;
                continue;
            }
            if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                if (end < 0) break; // shaderc reports malformed comments.
                i = end + 2;
                continue;
            }
            if (c == '#' && lineStart) directive = true;
            lineStart = false;
            int start = i++;
            if (Character.isLetterOrDigit(c) || c == '_') {
                while (i < source.length() && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) i++;
            } else if (c == '"') {
                while (i < source.length() && source.charAt(i++) != '"') {}
            }
            result.add(new Token(source.substring(start, i), start, i, directive));
        }
        return result;
    }

    private static ShaderTranslator.TranslationException unsupported(String detail) {
        return new ShaderTranslator.TranslationException("Unsupported GLSL compatibility form: " + detail);
    }
}

package dev.eviemod.compat;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;

/** Persisted pre-SDL hotkey integers, translated through vanilla's stable key names. */
public final class LegacyHotkeyCodes {
    private static final Map<Integer, String> NAMES = names();
    private LegacyHotkeyCodes() {}
    private static Map<Integer, String> names() {
        var names = new HashMap<Integer, String>();
        names.put(48, "key.keyboard.0");
        names.put(49, "key.keyboard.1");
        names.put(50, "key.keyboard.2");
        names.put(51, "key.keyboard.3");
        names.put(52, "key.keyboard.4");
        names.put(53, "key.keyboard.5");
        names.put(54, "key.keyboard.6");
        names.put(55, "key.keyboard.7");
        names.put(56, "key.keyboard.8");
        names.put(57, "key.keyboard.9");
        names.put(65, "key.keyboard.a");
        names.put(66, "key.keyboard.b");
        names.put(67, "key.keyboard.c");
        names.put(68, "key.keyboard.d");
        names.put(69, "key.keyboard.e");
        names.put(70, "key.keyboard.f");
        names.put(71, "key.keyboard.g");
        names.put(72, "key.keyboard.h");
        names.put(73, "key.keyboard.i");
        names.put(74, "key.keyboard.j");
        names.put(75, "key.keyboard.k");
        names.put(76, "key.keyboard.l");
        names.put(77, "key.keyboard.m");
        names.put(78, "key.keyboard.n");
        names.put(79, "key.keyboard.o");
        names.put(80, "key.keyboard.p");
        names.put(81, "key.keyboard.q");
        names.put(82, "key.keyboard.r");
        names.put(83, "key.keyboard.s");
        names.put(84, "key.keyboard.t");
        names.put(85, "key.keyboard.u");
        names.put(86, "key.keyboard.v");
        names.put(87, "key.keyboard.w");
        names.put(88, "key.keyboard.x");
        names.put(89, "key.keyboard.y");
        names.put(90, "key.keyboard.z");
        names.put(290, "key.keyboard.f1");
        names.put(291, "key.keyboard.f2");
        names.put(292, "key.keyboard.f3");
        names.put(293, "key.keyboard.f4");
        names.put(294, "key.keyboard.f5");
        names.put(295, "key.keyboard.f6");
        names.put(296, "key.keyboard.f7");
        names.put(297, "key.keyboard.f8");
        names.put(298, "key.keyboard.f9");
        names.put(299, "key.keyboard.f10");
        names.put(300, "key.keyboard.f11");
        names.put(301, "key.keyboard.f12");
        names.put(302, "key.keyboard.f13");
        names.put(303, "key.keyboard.f14");
        names.put(304, "key.keyboard.f15");
        names.put(305, "key.keyboard.f16");
        names.put(306, "key.keyboard.f17");
        names.put(307, "key.keyboard.f18");
        names.put(308, "key.keyboard.f19");
        names.put(309, "key.keyboard.f20");
        names.put(310, "key.keyboard.f21");
        names.put(311, "key.keyboard.f22");
        names.put(312, "key.keyboard.f23");
        names.put(313, "key.keyboard.f24");
        names.put(314, "key.keyboard.f25");
        names.put(282, "key.keyboard.num.lock");
        names.put(320, "key.keyboard.keypad.0");
        names.put(321, "key.keyboard.keypad.1");
        names.put(322, "key.keyboard.keypad.2");
        names.put(323, "key.keyboard.keypad.3");
        names.put(324, "key.keyboard.keypad.4");
        names.put(325, "key.keyboard.keypad.5");
        names.put(326, "key.keyboard.keypad.6");
        names.put(327, "key.keyboard.keypad.7");
        names.put(328, "key.keyboard.keypad.8");
        names.put(329, "key.keyboard.keypad.9");
        names.put(334, "key.keyboard.keypad.add");
        names.put(330, "key.keyboard.keypad.decimal");
        names.put(335, "key.keyboard.keypad.enter");
        names.put(336, "key.keyboard.keypad.equal");
        names.put(332, "key.keyboard.keypad.multiply");
        names.put(331, "key.keyboard.keypad.divide");
        names.put(333, "key.keyboard.keypad.subtract");
        names.put(264, "key.keyboard.down");
        names.put(263, "key.keyboard.left");
        names.put(262, "key.keyboard.right");
        names.put(265, "key.keyboard.up");
        names.put(39, "key.keyboard.apostrophe");
        names.put(92, "key.keyboard.backslash");
        names.put(44, "key.keyboard.comma");
        names.put(61, "key.keyboard.equal");
        names.put(96, "key.keyboard.grave.accent");
        names.put(91, "key.keyboard.left.bracket");
        names.put(45, "key.keyboard.minus");
        names.put(46, "key.keyboard.period");
        names.put(93, "key.keyboard.right.bracket");
        names.put(59, "key.keyboard.semicolon");
        names.put(47, "key.keyboard.slash");
        names.put(32, "key.keyboard.space");
        names.put(258, "key.keyboard.tab");
        names.put(342, "key.keyboard.left.alt");
        names.put(341, "key.keyboard.left.control");
        names.put(340, "key.keyboard.left.shift");
        names.put(343, "key.keyboard.left.win");
        names.put(346, "key.keyboard.right.alt");
        names.put(345, "key.keyboard.right.control");
        names.put(344, "key.keyboard.right.shift");
        names.put(347, "key.keyboard.right.win");
        names.put(257, "key.keyboard.enter");
        names.put(256, "key.keyboard.escape");
        names.put(259, "key.keyboard.backspace");
        names.put(261, "key.keyboard.delete");
        names.put(269, "key.keyboard.end");
        names.put(268, "key.keyboard.home");
        names.put(260, "key.keyboard.insert");
        names.put(267, "key.keyboard.page.down");
        names.put(266, "key.keyboard.page.up");
        names.put(280, "key.keyboard.caps.lock");
        names.put(284, "key.keyboard.pause");
        names.put(281, "key.keyboard.scroll.lock");
        names.put(348, "key.keyboard.menu");
        names.put(283, "key.keyboard.print.screen");
        names.put(161, "key.keyboard.world.1");
        names.put(162, "key.keyboard.world.2");
        names.put(0, "key.mouse.left");
        names.put(1, "key.mouse.right");
        names.put(2, "key.mouse.middle");
        for (int i = 3; i <= 7; i++) names.put(i, "key.mouse." + (i + 1));
        return Map.copyOf(names);
    }
    public static InputConstants.Key resolve(int stored) {
        String name = NAMES.get(stored);
        if (name == null) return InputConstants.UNKNOWN;
        try { return InputConstants.getKey(name); }
        catch (IllegalArgumentException ignored) { return InputConstants.UNKNOWN; }
    }
    public static int store(InputConstants.Key key) {
        return NAMES.entrySet().stream().filter(e -> e.getValue().equals(key.getName()))
            .mapToInt(Map.Entry::getKey).findFirst().orElse(-1);
    }
}

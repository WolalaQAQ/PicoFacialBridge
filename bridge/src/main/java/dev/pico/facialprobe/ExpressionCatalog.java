package dev.pico.facialprobe;

/** PICO source identifiers (not ARKit order); 52 is the EPI1 right inner-brow extension. */
final class ExpressionCatalog {
    static final String[] NAMES = {
        "EyeLookDownL", "NoseSneerL", "EyeLookInL", "BrowInnerUp", "BrowDownR", "MouthClose",
        "MouthLowerDownR", "JawOpen", "MouthUpperUpR", "MouthShrugUpper", "MouthFunnel",
        "EyeLookInR", "EyeLookDownR", "NoseSneerR", "MouthRollUpper", "JawRight", "BrowDownL",
        "MouthShrugLower", "MouthRollLower", "MouthSmileL", "MouthPressL", "MouthSmileR",
        "MouthPressR", "MouthDimpleR", "MouthLeft", "JawForward", "EyeSquintL", "MouthFrownL",
        "EyeBlinkL", "CheekSquintL", "BrowOuterUpL", "EyeLookUpL", "JawLeft", "MouthStretchL",
        "MouthPucker", "EyeLookUpR", "BrowOuterUpR", "CheekSquintR", "EyeBlinkR", "MouthUpperUpL",
        "MouthFrownR", "EyeSquintR", "MouthStretchR", "CheekPuff", "EyeLookOutL", "EyeLookOutR",
        "EyeWideR", "EyeWideL", "MouthRight", "MouthDimpleL", "MouthLowerDownL", "TongueOut",
        "BrowInnerUpR"
    };
    static final String[] CHINESE = {
        "左眼向下", "左侧皱鼻", "左眼向内", "眉心上抬", "右眉下压", "闭嘴",
        "右下唇下拉", "张嘴", "右上唇上提", "上唇耸起", "嘴唇漏斗",
        "右眼向内", "右眼向下", "右侧皱鼻", "上唇内卷", "下颌右移", "左眉下压",
        "下唇耸起", "下唇内卷", "左嘴角微笑", "左侧抿嘴", "右嘴角微笑",
        "右侧抿嘴", "右侧酒窝", "嘴部左移", "下颌前伸", "左眼眯起", "左嘴角下垂",
        "左眼闭合", "左脸颊上提", "左外眉上抬", "左眼向上", "下颌左移", "左嘴角拉伸",
        "噘嘴", "右眼向上", "右外眉上抬", "右脸颊上提", "右眼闭合", "左上唇上提",
        "右嘴角下垂", "右眼眯起", "右嘴角拉伸", "鼓腮", "左眼向外", "右眼向外",
        "右眼睁大", "左眼睁大", "嘴部右移", "左侧酒窝", "左下唇下拉", "伸舌",
        "右眉心上抬 · 独立"
    };
    static final class Row {
        final String chinese, english;
        final int left, right;
        Row(String chinese, String english, int left, int right) {
            this.chinese = chinese; this.english = english; this.left = left; this.right = right;
        }
        boolean matches(String query) {
            return chinese.contains(query) || english.toLowerCase(java.util.Locale.ROOT).contains(query)
                    || NAMES[left].toLowerCase(java.util.Locale.ROOT).contains(query)
                    || right >= 0 && NAMES[right].toLowerCase(java.util.Locale.ROOT).contains(query)
                    || CHINESE[left].contains(query) || right >= 0 && CHINESE[right].contains(query);
        }
    }
    static final Row[][] GROUPS = {
        {
            new Row("闭眼", "Blink", 28, 38),
            new Row("睁大", "Eye wide", 47, 46),
            new Row("眯眼", "Squint", 26, 41),
            new Row("眉心上抬", "Inner brow raise", 3, 52),
            new Row("眉毛下压", "Brow down", 16, 4),
            new Row("外眉上抬", "Outer brow raise", 30, 36),
            new Row("向上看", "Look up", 31, 35),
            new Row("向下看", "Look down", 0, 12),
            new Row("向内看", "Look in", 2, 11),
            new Row("向外看", "Look out", 44, 45)
        },
        {
            new Row("脸颊上提", "Cheek squint", 29, 37),
            new Row("皱鼻", "Nose sneer", 1, 13),
            new Row("鼓腮", "Cheek puff", 43, -1)
        },
        {
            new Row("张嘴", "Jaw open", 7, -1),
            new Row("闭嘴", "Mouth close", 5, -1),
            new Row("下颌侧移", "Jaw sideways", 32, 15),
            new Row("下颌前伸", "Jaw forward", 25, -1),
            new Row("微笑", "Smile", 19, 21),
            new Row("嘴角下垂", "Frown", 27, 40),
            new Row("嘴角拉伸", "Mouth stretch", 33, 42),
            new Row("酒窝", "Dimple", 49, 23),
            new Row("抿嘴", "Lip press", 20, 22),
            new Row("上唇上提", "Upper lip raise", 39, 8),
            new Row("下唇下拉", "Lower lip drop", 50, 6),
            new Row("嘴部侧移", "Mouth sideways", 24, 48),
            new Row("嘴唇漏斗", "Funnel", 10, -1),
            new Row("噘嘴", "Pucker", 34, -1),
            new Row("上唇内卷", "Upper lip roll", 14, -1),
            new Row("下唇内卷", "Lower lip roll", 18, -1),
            new Row("上唇耸起", "Upper lip shrug", 9, -1),
            new Row("下唇耸起", "Lower lip shrug", 17, -1),
            new Row("伸舌", "Tongue out", 51, -1)
        }
    };
    private ExpressionCatalog() {}
}

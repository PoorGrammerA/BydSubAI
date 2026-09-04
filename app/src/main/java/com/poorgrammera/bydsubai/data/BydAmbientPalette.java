package com.poorgrammera.bydsubai.data;

public enum BydAmbientPalette {
    PURPLE_PINKISH(1, "#CF39FF", "연분홍 / 핑크퍼플"),
    PURPLE(2, "#8C1FFA", "보라"),
    DEEP_BLUE(3, "#0024E9", "청색 / 파랑"),
    BLUE(4, "#008BF7", "파랑 / 하늘"),
    LIGHT_BLUE(5, "#00AAFF", "연청"),
    SKY_BLUE(6, "#07B7F1", "하늘"),
    CYAN(7, "#00E1F2", "청록 / 시안"),
    TEAL(8, "#00FFFF", "청록"),
    LIGHT_CYAN(9, "#45FEFE", "밝은 청록"),
    PASTEL_CYAN(10, "#A1FFF5", "파스텔 청록"),
    PASTEL_MINT(11, "#A1FFF9", "파스텔 민트"),
    MINT_GREEN(12, "#A7FBCE", "민트"),
    LIGHT_GREEN(13, "#6FEF9E", "연녹"),
    GREEN(14, "#17E85D", "초록"),
    YELLOW_GREEN(15, "#ADFB65", "연두"),
    LIME_YELLOW(16, "#E6F972", "라임"),
    PASTEL_YELLOW(17, "#FFFE99", "파스텔 노랑"),
    PASTEL_ORANGE(18, "#FFE88B", "파스텔 주황"),
    LIGHT_YELLOW(19, "#FFFF85", "연노랑"),
    YELLOW(20, "#FEFF50", "노랑"),
    BRIGHT_YELLOW(21, "#FFF855", "밝은 노랑"),
    GOLD_ORANGE(22, "#F0D117", "금색 / 귤색"),
    ORANGE(23, "#F9A846", "주황"),
    DARK_ORANGE(24, "#F9884A", "진한 주황"),
    CORAL(25, "#FF764E", "다홍 / 코랄"),
    RED(26, "#FF0049", "빨강"),
    PINKISH_RED(27, "#F63778", "진분홍"),
    PINK(28, "#F9728B", "분홍 / 핑크"),
    WHITE(29, "#FFFFFF", "흰색"),
    COOL_WHITE(30, "#E9FEFF", "백색"),
    VERY_LIGHT_BLUE(31, "#AFF8FF", "연하늘");

    private final int index;
    private final String hexColor;
    private final String koreanName;

    BydAmbientPalette(int index, String hexColor, String koreanName) {
        this.index = index;
        this.hexColor = hexColor;
        this.koreanName = koreanName;
    }

    public int getIndex() {
        return index;
    }

    public String getHexColor() {
        return hexColor;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public int getRgbColor() {
        return android.graphics.Color.parseColor(hexColor);
    }

    public static BydAmbientPalette fromIndex(int index) {
        for (BydAmbientPalette color : values()) {
            if (color.index == index) {
                return color;
            }
        }
        return WHITE;
    }

    public static BydAmbientPalette fromRgb(int rgbColor) {
        int r = (rgbColor >> 16) & 0xFF;
        int g = (rgbColor >> 8) & 0xFF;
        int b = rgbColor & 0xFF;

        BydAmbientPalette bestColor = WHITE;
        double minDistance = Double.MAX_VALUE;

        for (BydAmbientPalette color : values()) {
            int cRgb = color.getRgbColor();
            int cR = (cRgb >> 16) & 0xFF;
            int cG = (cRgb >> 8) & 0xFF;
            int cB = cRgb & 0xFF;

            double dist = Math.sqrt((r - cR) * (r - cR) + (g - cG) * (g - cG) + (b - cB) * (b - cB));
            if (dist < minDistance) {
                minDistance = dist;
                bestColor = color;
            }
        }
        return bestColor;
    }
}

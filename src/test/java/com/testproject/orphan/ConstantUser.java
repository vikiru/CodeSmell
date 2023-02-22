package com.testproject.orphan;

public class ConstantUser {
    private final int height;
    private final boolean isConstant;

    public ConstantUser() {
        this.height = setHeight(1001);
        this.isConstant = setIsConstant(false);
    }

    public boolean setIsConstant(boolean isConstant) {
        return isConstant == ConstantClass.IS_CONSTANT;
    }

    public int setHeight(int height) {
        return Math.max(height, ConstantClass.MAX_HEIGHT);
    }
}

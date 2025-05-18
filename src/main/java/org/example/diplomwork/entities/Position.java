package org.example.diplomwork.entities;

public class Position {
    private Integer x;
    private Integer y;

    public Position() {
        this.x = 0;
        this.y = 0;
    }

    public Position(Integer x, Integer y) {
        this.x = x;
        this.y = y;
    }

    public Integer getX() { return x; }

    public Integer getY() { return y; }

    public void setX(Integer x) { this.x = x; }

    public void setY(Integer y) { this.y = y; }

    @Override
    public String toString() {
        return "Position{"
                + "x=" + x
                + ", y=" + y + '}';
    }
}

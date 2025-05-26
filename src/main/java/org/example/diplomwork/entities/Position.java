package org.example.diplomwork.entities;

public class Position {
    private Double x;
    private Double y;

    public Position() {
        this.x = 0.;
        this.y = 0.;
    }

    public Position(Double x, Double y) {
        this.x = x;
        this.y = y;
    }

    public Double getX() { return x; }

    public Double getY() { return y; }

    public void setX(Double x) { this.x = x; }

    public void setY(Double y) { this.y = y; }

    @Override
    public String toString() {
        return "Position{"
                + "x=" + x
                + ", y=" + y + '}';
    }
}

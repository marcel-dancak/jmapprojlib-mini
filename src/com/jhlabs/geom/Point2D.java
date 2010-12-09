package com.jhlabs.geom;

public final class Point2D {

	public double x;
	public double y;
	
	public Point2D() {
	}
	
	public Point2D(double x, double y) {
		this.x = x;
		this.y = y;
	}
	
	@Override
	public final String toString() {
		return String.format("[%f, %f]", x, y);
	}
}

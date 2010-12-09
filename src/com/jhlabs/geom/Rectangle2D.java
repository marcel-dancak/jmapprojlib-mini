package com.jhlabs.geom;

public final class Rectangle2D {

	public double x;
	public double y;
	public double width;
	public double height;
	
	public Rectangle2D() {
	}

	public Rectangle2D(double x, double y, double width, double height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	/**
     * Adds a point, specified by the double precision arguments
     * <code>newx</code> and <code>newy</code>, to this 
     * <code>Rectangle2D</code>.  The resulting <code>Rectangle2D</code> 
     * is the smallest <code>Rectangle2D</code> that
     * contains both the original <code>Rectangle2D</code> and the
     * specified point.
     * <p>
     * After adding a point, a call to <code>contains</code> with the 
     * added point as an argument does not necessarily return 
     * <code>true</code>. The <code>contains</code> method does not 
     * return <code>true</code> for points on the right or bottom 
     * edges of a rectangle. Therefore, if the added point falls on 
     * the left or bottom edge of the enlarged rectangle, 
     * <code>contains</code> returns <code>false</code> for that point.
     * @param newx the X coordinate of the new point
     * @param newy the Y coordinate of the new point
     */
    public void add(double newx, double newy) {
    	double x1 = Math.min(x, newx);
    	double x2 = Math.max(x+width, newx);
    	double y1 = Math.min(y, newy);
    	double y2 = Math.max(y+height, newy);
    	setRect(x1, y1, x2 - x1, y2 - y1);
    }
    
    public void setRect(double x, double y, double width, double height) {
    	this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
    }
    
	public double getX() {
		return x;
	}

	public void setX(double x) {
		this.x = x;
	}

	public double getY() {
		return y;
	}

	public void setY(double y) {
		this.y = y;
	}

	public double getWidth() {
		return width;
	}

	public void setWidth(double width) {
		this.width = width;
	}

	public double getHeight() {
		return height;
	}

	public void setHeight(double height) {
		this.height = height;
	}
	
}

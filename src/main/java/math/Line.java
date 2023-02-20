/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package math; 

import static java.lang.Math.*;
/**
 *
 * @author GBEMIRO
 */
public class Line{

    private Double m;
    private Double c;


/**
 * Creates a straight line parallel to the x axis
 */
    public Line(){
        this.m=0d;
        this.c=0d;
    }
/**
 * Creates a new Line object give the gradient and the y intercept
 * @param m the gradient of the Line object
 * @param c the y intercept of the Line object
 */
    public Line(Double m,Double c){
this.m=m;
this.c=c;
    }

/**
 * Creates a new Line object give the gradient and a point on the line.
 * @param m the gradient of the Line object
 * @param p a Point object that lies on the Line object.
 */
    public Line(Double m,Point p){
this.m=m;
this.c=p.y-(m*p.x);
    }

/**
 * Creates a line between points joining: the points x1,y1 and x2,y2
 * @param x1 the x coordinate of the first point
 * @param y1 the y coordinate of the first point
 * @param x2 the x coordinate of the second point
 * @param y2 the y coordinate of the second point
 */
    public Line(Double x1,Double y1, Double x2,Double y2){
        this.m= new Point(x1,y1).findXYGrad(new Point(x2,y2));
        this.c=y1-this.m*x1;
    }





 /**
 * Creates a line between points joining: the points p1 and p2
  * @param p1 the first point
  * @param p2 the second point
  */
    public Line(Point p1,Point p2){
        this.m= p1.findXYGrad(p2);
        this.c=p1.y-this.m*p1.x;
    }


/**
 *
 * @param m sets the gradient of this Line.
 */
    public void setM(Double m) {
        this.m = m;
    }
/**
 *
 * @return  the gradient of this Line.
 */
    public Double getM() {
        return m;
    }
/**
 *
 * @param c sets the intercept of this Line.
 */
    public void setC(Double c) {
        this.c = c;
    }
/**
 *
 * @return the intercept of this Line.
 */
    public Double getC() {
        return c;
    }


/**
 *
 * @param y the y coordinate of
 * a given point on a Line object.
 * @return the x coordinate of that point.
 */
public Double getX(Double y){
    return (y-this.c)/this.m;
}
/**
 *
 * @param x the x coordinate of
 * a given point on a Line object.
 * @return the y coordinate of that point.
 */
public Double getY(Double x){
 return (this.m*x)+this.c;
}


/**
 *
 * Finds the distance between 2 Point objects lying on this Line object
 * They must lie on this Line object, else the method will return 0;
 * @param p1 the first Point object to consider
 * @param p2 the second Point object to consider
 * @return the distance along this Line
 * object between the 2 given Point objects lying on it
 */
public Double distance(Point p1,Point p2){
    Double dist=0d;
    if(passesThroughPoint(p1)&&passesThroughPoint(p2)){
       dist = p2.calcDistanceTo(p1);
    }
    return dist;
}

/**
 *
 * Finds the square of the distance between 2 Point objects lying on this Line object
 * They must lie on this Line object, else the method will return 0;
 * @param p1 the first Point object to consider
 * @param p2 the second Point object to consider
 * @return the distance along this Line
 * object between the 2 given Point objects lying on it
 */
public Double distanceSquared(Point p1,Point p2){
    Double dist=0d;
    if(passesThroughPoint(p1)&&passesThroughPoint(p2)){
       dist = pow( (p2.x-p1.x),2 )+pow( (p2.y-p1.y),2 );
    }
    return dist;
}




/**
 *
 * @param line the Line object to be checked if or not it intersects with this one.
 * @return true if the 2 Line objects intersect.
 */
public boolean intersectsLine(Line line){
return !isParallelTo(line);
}
/**
 * Checks if this Line object is parallel to another.
 * @param line the Line object to be checked against this one for parallelism
 * @return true if it is parallel to the other Line object
 */
public boolean isParallelTo(Line line){
    return approxEquals(this.m, line.m);
}

/**
 * 
 * @param p1 the Point object that we
 * wish to check if or not it lies on this Line
 * object.
 * @return true if it lies on this Line object
 */
    public boolean passesThroughPoint(Point p1){
    return approxEquals( p1.y, (this.m*p1.x+this.c) );
    }
/**
 *
 * @param line the Line object whose point of
 * intersection with this Line object is required
 * @return the point of intersection of both Line objects
 */
public Point intersectionWithLine(Line line){
    Double x= (-1*(this.c-line.c)/(this.m-line.m));
    Double y= this.m*x+this.c;
    return new Point(x,y);
}


/**
 *  Draws this Line object for the interval between x1 and x2.
 * @param g The Graphics object used to draw this Line object.
 * @param x1 The x coordinate of the first point on this Line object
 * where drawing is to start
 * @param x2 The x coordinate of the second point on this Line object
 * where drawing is to start
 */
public void draw(Object g,Double x1, Double x2){


}


 /**
 * Compares two numbers to see if they are close enough to be almost the same
 * It checks if the values deviate by 1.0E-14 or lesser.
 * @param val1 the first value to compare
 * @param val2 the second value to compare
 * @return true if the values deviate by 1.0E-14 or lesser.
 */

public boolean approxEquals(Double val1,Double val2){
 return abs(abs(val1)-abs(val2))<=1.0E-14;
}

/**
 * Compares two numbers to see if they are close enough to be almost the same
 * It checks if the values deviate by 1.0E-14 or lesser.
 * @param val1 the first value to compare
 * @param val2 the second value to compare
 * @param minDeviation the minimum difference they
 * must have to be acceptably equal.
 * @return true if the values deviate by 1.0E-14 or lesser.
 */

public boolean approxEquals(Double val1,Double val2,Double minDeviation){
 return abs(abs(val1)-abs(val2))<=abs(minDeviation);
}




    @Override
public String toString(){
    return "y = "+this.m+"*x + "+this.c;
}



      public static void main(String args[]){
    Line ell=new Line(2d,4d,-2d,-1d);

    System.out.println(ell.toString() );

    }

}//end class Line

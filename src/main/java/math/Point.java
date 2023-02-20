/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package math;

import util.SimplePoint;

import static java.lang.Math.*;
/**
 *
 * @author GBEMIRO
 */
public class Point {
public Double x;
public Double y;
public Double z;
/**
 * ICreates a Point object at the origin of
 * 3D space.
 */
public Point(){
    this.x=0d;
    this.y=0d;
    this.z=0d;
}
/**
 * Initializes a 1D Point object
 * @param x the x coordinate of the Point object
 */
public Point(Double x){
    this.x=x;
    this.y=0d;
    this.z=0d;
}
/**
 * Initializes a 2D Point object
 * @param x the x coordinate of the Point object
 * @param y the y coordinate of the Point object
 */
public Point(Double x,Double y){
    this.x=x;
    this.y=y;
    this.z=0d;
}


/**
 * Initializes a 2D Point object
 * @param x the x coordinate of the Point object
 * @param y the y coordinate of the Point object
 * @param z the z coordinate of the Point object
 */

    public Point(Double x, Double y, Double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }


/**
 * Creates a new Point object
 * similar to this one but not referring
 * to the same object
 * @param point The point to mutate
 */
    public Point(Point point){
        this.x=point.x;
        this.y=point.y;
        this.z=point.z;
    }

/**
 *
 * @param x sets x the x coordinate of the Point object
 */
    public void setX(Double x) {
        this.x = x;
    }
/**
 *
 * @return x the x coordinate of the Point object
 */
    public Double getX() {
        return x;
    }
/**
 *
 * @param y x the y coordinate of the Point object
 */
    public void setY(Double y) {
        this.y = y;
    }
/**
 *
 * @return x the y coordinate of the Point object
 */
    public Double getY() {
        return y;
    }
/**
 *
 * @param z sets the z coordinate of the Point object
 */
    public void setZ(Double z) {
        this.z = z;
    }
/**
 *
 * @return the z coordinate of the Point object
 */
    public Double getZ() {
        return z;
    }
/***
 *
 * @param pt the Point object whose distance to this Point object is required
 * @return the distance between this Point object and Point pt
 */

public Double calcDistanceTo(Point pt){
    return sqrt(  pow( (x-pt.x),2)+pow( (y-pt.y),2)+pow( (z-pt.z),2) );
}

/**
 *
 * @param pt the point between which an imaginary line runs
 * @return the gradient of the projection of the line joining
 * these points on the XY plane
 */
public Double findXYGrad(Point pt){
    return (y-pt.y)/(x-pt.x);
}

/**
 *
 * @param pt the point between which an imaginary line runs
 * @return the gradient of the projection of the line joining
 * these points on the XZ plane
 */
public Double findXZGrad(Point pt){
    return (z-pt.z)/(x-pt.x);
}

/**
 *
 * @param pt the point between which an imaginary line runs
 * @return the gradient of the projection of the line joining
 * these points on the YZ plane
 */
public Double findYZGrad(Point pt){
    return (z-pt.z)/(y-pt.y);
}



/**
 * Converts objects of this class to
 * the normal Point object.
 * @return a java.awt.Point object from
 * this Point object
 */
public SimplePoint getUtilPoint(){
    return new SimplePoint(  (int)this.x.intValue(),(int)this.y.intValue()  );
}
/**
 * Converts objects of this class to
 * the normal Point object.
 * @param point
 * @return a java.awt.Point object from
 * an object of this class.
 */
public static SimplePoint getUtilPoint(Point point){
    return new SimplePoint(  (int)point.x.intValue(),(int)point.y.intValue()  );
}


/**
 *
 * @param p1 The first Point object.
 * @param p2 The second Point object.
 * @return The Point object that contains the coordinates
 * of the midpoint of the line joining p1 and p2
 */
public static Point midPoint(Point p1,Point p2){
    return new Point(0.5*(p1.x+p2.x),0.5*(p1.y+p2.y)  );
}

/**
 *
 * @param p1
 * @param p2
 * @return true if the 2 points and this one
 * lie on the same straight line.
 */
public boolean isCollinearWith(Point p1,Point p2){
    Line line=new Line(p1,p2);
    return line.passesThroughPoint(this);
}


/**
 *
 * @param p1
 * @param p2
 * @return true if this Point object lies on the same
 * straight line with p1 and p2 and it lies in between them.
 */
public boolean liesBetween(Point p1,Point p2){
    boolean truly1 = ( (p1.x<=x&&p2.x>=x)||(p2.x<=x&&p1.x>=x)  );
boolean truly2 = ( (p1.y<=y&&p2.y>=y)||(p2.y<=y&&p1.y>=y)  );
boolean truly3 = ( (p1.z<=z&&p2.z>=z)||(p2.z<=z&&p1.z>=z)  );

return truly1&&truly2&&truly3&&isCollinearWith(p1, p2); 
}


    @Override
public String toString(){

        if(z==0){
         return "("+x+", "+y+")" ;
        }
        else{
             return "("+x+", "+y+", "+z+")" ;
        }

   
}


public static void main(String args[]){
    Point p1= new Point(1d,5d);
    Point p2=new Point(2d,8d);
    Point p3=new Point(10d,32d);
    System.out.println(p1.liesBetween(p2, p3));

}


}

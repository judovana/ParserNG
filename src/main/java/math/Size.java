/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package math;

import util.Dimension;


/**
 *
 * @author GBEMIRO
 */
public class Size {
public Double width;
public Double height;
/**
 * Creates a new object of this class.
 * @param width Stores the width.
 * @param height Stores the height.
 */
    public Size(Double width, Double height) {
        this.width = width;
        this.height = height;
    }//end constructor
/**
 * Creates a Size object from
 * a java.awt.Dimension object.
 * @param dim The givem Dimension object.
 */
public Size(Dimension dim){
    this.width=(double)dim.width;
    this.height=(double)dim.height;
}

/**
 * Creates a new Size object from
 * a given Size object.
 * The created object is exactly similar in value to the
 * given one, but is not the same as that one.It is a sort of twin.
 * @param size The given Size object.
 */
public Size(Size size){
    this.width=size.width;
    this.height=size.height;
}

/**
 *
 * @return The height
 */
    public Double getHeight() {
        return height;
    }//end method
/**
 *
 * @param height sets the height
 */
    public void setHeight(Double height) {
        this.height = height;
    }//end method
/**
 *
 * @return the width
 */
    public Double getWidth() {
        return width;
    }//end method
/**
 *
 * @param width sets the height
 */
    public void setWidth(Double width) {
        this.width = width;
    }//end method
    /**
     *
     * @param width sets the width attribute of this object
     * @param height sets the height attribute of this object
     */
public void setSize(Double width,Double height){
    this.width=width;
    this.height=height;
}//end method

/**
 *
 * @param size sets the size of object of this class
 * using an already known one.
 *
 */
public void setSize(Size size){
    this.width=size.width;
    this.height=size.height;
}//end method


/**
 *
 * @return a new instance of Size containing
 * information about the size of this object
 */
public Size getSize(){
    return new Size(width,height);
}//end method

/**
 *
 * @param scaleFactor the factor by which this Size object is to be scaled.
 */
public void scale(Double scaleFactor){
    setSize(scaleFactor*width, scaleFactor*height);
}

/**
 *returns a new Size object scaled to the value given by scaleFactor
 * @param scaleFactor the factor by which this Size object is to be scaled.
 */
public Size getScaledInstance(Double scaleFactor){
    return new Size(scaleFactor*width, scaleFactor*height);
}
/**
 *returns a new Size object scaled to the value given by scaleWidth
 * and scaleHeight, both being muktipliers for the width and the
 * height.
 * @param scaleWidth  the factor by which the width of this Size object is to be scaled.
 * @param scaleHeight    the factor by which the height of this Size object is to be scaled.
 */
public Size getScaledInstance(Double scaleWidth,Double scaleHeight){
    return new Size(scaleWidth*width, scaleHeight*height);
}


    @Override
    public String toString() {
     return "( width = "+width+  ",height = "+height+" ).";

    }

/**
 *
 * @return  the Dimension object
 * closest in value to this Size object.
 */
public Dimension getAWTDimension(){
    return new Dimension(width.intValue(),height.intValue()   );
}












}//end class

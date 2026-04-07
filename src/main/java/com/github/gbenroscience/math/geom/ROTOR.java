/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.gbenroscience.math.geom;

import com.github.gbenroscience.parser.Function;
import com.github.gbenroscience.parser.MathExpression;
import com.github.gbenroscience.parser.Scanner;
import com.github.gbenroscience.parser.methods.Method;
import com.github.gbenroscience.util.FunctionManager;
import java.awt.Polygon;
import java.awt.Rectangle;
import static java.lang.Math.*;
import java.util.Arrays;
import java.util.List;

/**
 * Create a variable or use a constant for the angle Create matrix vectors for O
 * and D Where O is the origin or point about which rotation will occur, and D
 * are the direction coordinates(a,b,c) of the rotation
 * rot(F,angle,origin,direction)
 *
 * So: rot(@(x,y,z)sin(x-y-3*z),PI,@(2,1)(2,2),@(3,1)(1,-2,3))
 * rot(@(x,y,z)sin(x-y-3*z),PI,@(2,1)(2,2),@(3,1)(1,-2,3))
 *
 * @author GBEMIRO
 */
public class ROTOR {

    /**
     * The angle of rotation
     */
    private double angle;
    /**
     * The point about which this rotation occurs.
     */
    private Point rotorCenter;

    /**
     * The direction indices of rotation.
     *
     * e.g if a body is to be rotated in such a way that a particular point on
     * it will always move in the plane 2x+3y+4z+45=23 Then the direction
     * indices of rotation are 2,3,4. They are stored in a point object.
     *
     * These are very important and must be supplied correctly to the system.
     * When rotation occurs, for a Point object, it occurs in a single plane
     * whose direction vector or direction indices dictate or specify the
     * direction of rotation. For a Line object, it occurs in a multitude of
     * parallel planes which being parallel will have a common direction vector.
     * This direction vector specifies the direction of rotation.
     *
     * To carry out rotation in the XY plane, note that z = 0 must be set in the
     * Direction attribute. i.e Direction dir = new Direction(0.2,3,0);
     *
     * Some important directions are:
     *
     * Planes parallel to the xy plane:..new Direction(0,0,constant), where
     * constant is any number greater than 0. Planes parallel to the xz
     * plane:..new Direction(0,constant,0), where constant is any number greater
     * than 0. Planes parallel to the yz plane:..new Direction(constant,0,0),
     * where constant is any number greater than 0.
     *
     * In general, for any plane with equation, ax+by +cz+d=0, its direction is
     * new Direction(a,b,c)
     *
     *
     *
     */
    private Direction direction;

    /**
     * This array contains the names given to the 3 axes that this rotor is to
     * deal with. CARE!!! The first name in it is assigned to the x axis. The
     * second name in it is assigned to the y axis. The third name in it is
     * assigned to the z axis.
     *
     * The default is the normal coordinate axes..i.e x,y,z.
     *
     * The contents should only be changed when a Function is to be rotated
     * which does not use the normal x,y,z names for the 3 coordinate axes.
     *
     */
    private final String[] parameters = new String[]{"x", "y", "z"};

    /**
     * Creates a new object of class ROTOR that can be used to rotate Point
     * objects, Line objects, and Plane objects, Function objects and so on.
     *
     * This constructor defaults the rotor center, i.e the point about which the
     * rotation will occur to zero.
     *
     * @param angle The angle of rotation
     * @param direction The direction in which rotation will occur. When
     * rotation occurs, for a Point object, it occurs in a single plane whose
     * direction vector or direction indices dictate or specify the direction of
     * rotation. For a Line object, it occurs in a multitude of parallel planes
     * which being parallel will have a common direction vector. This direction
     * vector specifies the direction of rotation.
     */
    public ROTOR(double angle, Direction direction) {
        this.angle = angle;
        this.rotorCenter = new Point(0, 0, 0);
        this.direction = direction;

    }//end constructor

    /**
     *
     * Creates a new object of class ROTOR that can be used to rotate Point
     * objects, Line objects, and Plane objects, Function objects and so on.
     *
     * @param angle The angle of rotation
     * @param rotorCenter The Point object about which the rotation will occur.
     * @param direction The direction in which rotation will occur. When
     * rotation occurs, for a Point object, it occurs in a single plane whose
     * direction vector or direction indices dictate or specify the direction of
     * rotation. For a Line object, it occurs in a multitude of parallel planes
     * which being parallel will have a common direction vector. This direction
     * vector specifies the direction of rotation.
     */
    public ROTOR(double angle, Point rotorCenter, Direction direction) {
        this.angle = angle;
        this.rotorCenter = rotorCenter;
        this.direction = direction;
    }//end constructor

    /**
     *
     * @return the angle of rotation of the object
     */
    public double getAngle() {
        return angle;
    }//end method

    /**
     *
     * @param angle sets the angle of rotation
     */
    public void setAngle(double angle) {
        this.angle = angle;
    }//end method

    /**
     * The direction indices of rotation.
     *
     * e.g if a body is to be rotated in such a way that a particular point on
     * it will always move in the plane 2x+3y+4z+45=23 Then the direction
     * indices of rotation are 2,3,4. They are stored in a point object.
     *
     * @param direction sets the direction indices of rotation These are very
     * important and must be supplied correctly to the system.
     *
     */
    public void setDirection(Direction direction) {
        this.direction = direction;
    }//end method

    /**
     * The direction indices of rotation.
     *
     * e.g if a body is to be rotated in such a way that a particular point on
     * it will always move in the plane 2x+3y+4z+45=23 Then the direction
     * indices of rotation are 2,3,4. They are stored in a point object.
     *
     * These are very important and must be supplied correctly to the system.
     *
     * @return the direction indices of rotation
     */
    public Direction getDirection() {
        return direction;
    }//end method

    /**
     *
     * @return the coordinates of the point about which rotation occurs.
     */
    public Point getRotorCenter() {
        return rotorCenter;
    }//end method

    /**
     *
     * @param name sets the name of the x axis. The default is x.
     */
    public void setXAxisName(String name) {
        parameters[0] = name;
    }//end method

    /**
     *
     * @param name sets the name of the y axis. The default is y.
     */
    public void setYAxisName(String name) {
        parameters[1] = name;
    }//end method

    /**
     *
     * @param name sets the name of the z axis. The default is z.
     */
    public void setZAxisName(String name) {
        parameters[2] = name;
    }//end method

    /**
     *
     * @return an array containing the names of all the 3 axes.
     */
    public String[] getCoordinateAxesName() {
        return parameters;
    }

    /**
     *
     * @param rotorCenter sets the coordinates of the point about which rotation
     * occurs.
     */
    public void setRotorCenter(Point rotorCenter) {
        this.rotorCenter = rotorCenter;
    }//end method

    /**
     *
     * @return a string expression of the rotation function of x;
     */
    private String rotXOld() {

        double sin = sin(angle);
        double cos = cos(angle);
        double a = direction.a;
        double b = direction.b;
        double c = direction.c;

        double k = sqrt(pow(a, 2) + pow(b, 2) + pow(c, 2));
        double K = pow(k, 2);//The square of k;
        double A = pow(a, 2);
        double B = pow(b, 2);
        double C = pow(c, 2);

        Point cen = getRotorCenter();

        double x1 = cen.x;
        double y1 = cen.y;
        double z1 = cen.z;

        String genX = ((K * cos + A * (1 - cos)) / K) + parameters[0];

        String genY = ((k * c * sin - a * b * (1 - cos)) / K) + parameters[1];

        String genZ = (((k * b * sin + a * c * (1 - cos)) / K)) + parameters[2];

        String constant = "" + (((B + C) * (1 - cos) * x1 + (k * c * sin - a * b * (1 - cos)) * y1 - (k * b * sin + a * c * (1 - cos)) * z1) / K);

        return genX + "-" + genY + "+" + genZ + "+" + constant;
    }//end method

    /**
     *
     * @return a string expression of the rotation function of y;
     */
    private String rotYOld() {

        double sin = sin(angle);
        double cos = cos(angle);
        double a = direction.a;
        double b = direction.b;
        double c = direction.c;

        double k = sqrt(pow(a, 2) + pow(b, 2) + pow(c, 2));
        double K = pow(k, 2);//The square of k;
        double A = pow(a, 2);
        double B = pow(b, 2);
        double C = pow(c, 2);

        Point cen = getRotorCenter();

        double x1 = cen.x;
        double y1 = cen.y;
        double z1 = cen.z;

        String genX = ((a * b * (1 - cos) + k * c * sin) / K) + parameters[0];

        String genY = ((B * (1 - cos) + K * cos) / K) + parameters[1];

        String genZ = (((k * a * sin - b * c * (1 - cos)) / K)) + parameters[2];

        String constant = "" + (((-k * c * sin - a * b * (1 - cos)) * x1 + (A + C) * (1 - cos) * y1 + (k * a * sin - b * c * (1 - cos)) * z1) / K);

        return genX + "+" + genY + "-" + genZ + "+" + constant;
    }//end method

    /**
     *
     * @return a string expression of the rotation function of z;
     */
    private String rotZOld() {

        double sin = sin(angle);
        double cos = cos(angle);
        double a = direction.a;
        double b = direction.b;
        double c = direction.c;

        double k = sqrt(pow(a, 2) + pow(b, 2) + pow(c, 2));
        double K = pow(k, 2);//The square of k;
        double A = pow(a, 2);
        double B = pow(b, 2);
        double C = pow(c, 2);

        Point cen = getRotorCenter();

        double x1 = cen.x;
        double y1 = cen.y;
        double z1 = cen.z;

        String genX = ((a * c * (1 - cos) - k * b * sin) / K) + parameters[0];

        String genY = ((k * a * sin + b * c * (1 - cos)) / K) + parameters[1];

        String genZ = (((K * cos + C * (1 - cos)) / K)) + parameters[2];

        String constant = "" + (((k * b * sin - a * c * (1 - cos)) * x1 - (k * a * sin + b * c * (1 - cos)) * y1 + (A + B) * (1 - cos) * z1) / K);

        return genX + "+" + genY + "+" + genZ + "+" + constant;
    }//end method

    private String formatTerm(double coeff, String var, boolean isFirst) {
        if (Math.abs(coeff) < 1e-10) {
            return ""; // Effectively zero
        }
        String sCoeff;
        if (coeff == 1.0) {
            sCoeff = "";
        } else if (coeff == -1.0) {
            sCoeff = "-";
        } else {
            sCoeff = String.format("%.16f", coeff); // Limits decimal gore
        }
        // Handle leading plus signs
        String sign = (coeff > 0 && !isFirst) ? "+" : "";

        Function f = FunctionManager.lookUp(var);
        if (f != null) {
            String expr = f.getMathExpression().getExpression();
            return sign + sCoeff + "*" + ((expr.startsWith("(") && expr.endsWith(")")) ? expr : "(" + expr + ")");
        }
        return sign + sCoeff + "*" + var;
    }

    private String rotX() {
        double sin = sin(angle);
        double cos = cos(angle);
        double a = direction.getA(), b = direction.getB(), c = direction.getC();
        double k = sqrt(a * a + b * b + c * c);
        double K = k * k;

        Point cen = getRotorCenter();
        double x1 = cen.x, y1 = cen.y, z1 = cen.z;

        // Coefficients for x, y, z and the constant term
        double cx = (K * cos + (a * a) * (1 - cos)) / K;
        double cy = -((k * c * sin - a * b * (1 - cos)) / K);
        double cz = (k * b * sin + a * c * (1 - cos)) / K;
        double cConst = (((b * b + c * c) * (1 - cos) * x1 + (k * c * sin - a * b * (1 - cos)) * y1 - (k * b * sin + a * c * (1 - cos)) * z1) / K);

        StringBuilder sb = new StringBuilder();
        sb.append(formatTerm(cx, parameters[0], true));
        sb.append(formatTerm(cy, parameters[1], sb.length() == 0));
        sb.append(formatTerm(cz, parameters[2], sb.length() == 0));

        if (Math.abs(cConst) > 1e-10) {
            sb.append(cConst > 0 ? "+" : "").append(String.format("%.16f", cConst));
        }

        return sb.toString();
    }

    /**
     * Returns a clean string expression for the rotated Y coordinate.
     */
    private String rotY() {
        double sin = sin(angle);
        double cos = cos(angle);
        double a = direction.getA(), b = direction.getB(), c = direction.getC();
        double k = sqrt(a * a + b * b + c * c);
        double K = k * k;

        Point cen = getRotorCenter();
        double x1 = cen.x, y1 = cen.y, z1 = cen.z;

        // Coefficients derived from original Rodrigues logic
        double cx = (a * b * (1 - cos) + k * c * sin) / K;
        double cy = ((b * b) * (1 - cos) + K * cos) / K;
        double cz = -((k * a * sin - b * c * (1 - cos)) / K);
        double cConst = ((-k * c * sin - a * b * (1 - cos)) * x1 + (a * a + c * c) * (1 - cos) * y1 + (k * a * sin - b * c * (1 - cos)) * z1) / K;

        StringBuilder sb = new StringBuilder();
        sb.append(formatTerm(cx, parameters[0], true));
        sb.append(formatTerm(cy, parameters[1], sb.length() == 0));
        sb.append(formatTerm(cz, parameters[2], sb.length() == 0));

        if (Math.abs(cConst) > 1e-10) {
            sb.append(cConst > 0 ? "+" : "").append(String.format("%.16f", cConst));
        }

        return sb.toString();
    }

    /**
     * Returns a clean string expression for the rotated Z coordinate.
     */
    private String rotZ() {
        double sin = sin(angle);
        double cos = cos(angle);
        double a = direction.getA(), b = direction.getB(), c = direction.getC();
        double k = sqrt(a * a + b * b + c * c);
        double K = k * k;

        Point cen = getRotorCenter();
        double x1 = cen.x, y1 = cen.y, z1 = cen.z;

        // Coefficients derived from original Rodrigues logic
        double cx = (a * c * (1 - cos) - k * b * sin) / K;
        double cy = (k * a * sin + b * c * (1 - cos)) / K;
        double cz = (K * cos + (c * c) * (1 - cos)) / K;
        double cConst = ((k * b * sin - a * c * (1 - cos)) * x1 - (k * a * sin + b * c * (1 - cos)) * y1 + (a * a + b * b) * (1 - cos) * z1) / K;

        StringBuilder sb = new StringBuilder();
        sb.append(formatTerm(cx, parameters[0], true));
        sb.append(formatTerm(cy, parameters[1], sb.length() == 0));
        sb.append(formatTerm(cz, parameters[2], sb.length() == 0));

        if (Math.abs(cConst) > 1e-10) {
            sb.append(cConst > 0 ? "+" : "").append(String.format("%.16f", cConst));
        }

        return sb.toString();
    }

    /**
     * Method for rotating a Point object in the XY plane or in any plane
     * parallel to the XY plane. The Point object to be rotated and the one
     * about which it is rotating must have the same Z coordinates.
     *
     * @param angle the angle of rotation
     * @param cen a Point object about which the rotation of the first Point
     * object will occur.
     * @param p a Point object in the xy plane The z coordinates of both Point
     * objects must be either the same or must both be zero.
     * @return a Point object in the xy plane rotated through the angle
     */
    public static Point planarXYRotate(Point p, Point cen, double angle) {

        double sin = sin(angle);
        double cos = cos(angle);
        double X = p.x * cos - p.y * sin + cen.x * (1 - cos) + cen.y * sin;
        double Y = p.x * sin + p.y * cos + cen.y * (1 - cos) - cen.x * sin;
        return new Point(X, Y, p.z);
    }//end method

    /**
     *
     * @param p The Point object to be rotated.
     * @return a Point object that is the new Point object obtained by rotating
     * the Point object parameter through the angle attribute of this ROTOR
     * object and about the Point attribute of this class, i.e (rotorCenter) and
     * also in the given direction.
     *
     */
    public Point rotate(Point p) {
        double sin = sin(angle);
        double cos = cos(angle);
        double a = direction.a;
        double b = direction.b;
        double c = direction.c;

        double k = sqrt(pow(a, 2) + pow(b, 2) + pow(c, 2));
        double K = k * k;//The square of k;
        double A = a * a;
        double B = b * b;
        double C = c * c;

        Point cen = getRotorCenter();
        double x = p.x;
        double y = p.y;
        double z = p.z;
        double x1 = cen.x;
        double y1 = cen.y;
        double z1 = cen.z;

        double X = ((K * cos + A * (1 - cos)) * x - (k * c * sin - a * b * (1 - cos)) * y + (k * b * sin + a * c * (1 - cos)) * z
                + (B + C) * (1 - cos) * x1 + (k * c * sin - a * b * (1 - cos)) * y1 - (k * b * sin + a * c * (1 - cos)) * z1) / K;

        double Y = ((a * b * (1 - cos) + k * c * sin) * x + (B * (1 - cos) + K * cos) * y - (k * a * sin - b * c * (1 - cos)) * z
                - (a * b * (1 - cos) + k * c * sin) * x1 + (A + C) * (1 - cos) * y1 + (k * a * sin - b * c * (1 - cos)) * z1) / K;

        double Z = ((a * c * (1 - cos) - k * b * sin) * x + (k * a * sin + b * c * (1 - cos)) * y + (K * cos + C * (1 - cos)) * z
                - (a * c * (1 - cos) - k * b * sin) * x1 - (k * a * sin + b * c * (1 - cos)) * y1 + (A + B) * (1 - cos) * z1) / K;

        return new Point(X, Y, Z);
    }//end method

    /**
     *
     * @param line The Line3D object to be rotated.
     * @return a Line3D object that is the new Line3D object obtained by
     * rotating the Line3D object parameter through the angle attribute of this
     * ROTOR object and about the Point attribute of this class, i.e
     * (rotorCenter) and also in the given direction.
     *
     */
    public Line3D rotate(Line3D line) {
//Get 2 points on the line.
        double x = line.getXyLine().getX(5);
        Point p1 = new Point(x, 5, line.getXzLine().getY(x));
        x = line.getXyLine().getX(10);
        Point p2 = new Point(x, 10, line.getXzLine().getY(x));

        return new Line3D(rotate(p1), rotate(p2));
    }//end method

    /**
     *
     * @param plane The Plane object to be rotated.
     * @return a Plane object that is the new Plane object obtained by rotating
     * the Plane object parameter through the angle attribute of this ROTOR
     * object and about the Point attribute of this class, i.e (rotorCenter) and
     * also in the given direction.
     *
     */
    public Plane rotate(Plane plane) {
//Get three points on the Plane.
        double x = 2;
        double y = 10;
        Point p1 = new Point(x, y, plane.getZ(x, y));
        x = 4;
        y = 13;
        Point p2 = new Point(x, y, plane.getZ(x, y));
        x = 21;
        y = -13;
        Point p3 = new Point(x, y, plane.getZ(x, y));

        return new Plane(rotate(p1), rotate(p2), rotate(p3));
    }//end method

    /**
     *
     * @return the String representation of this object describing its
     * attributes as at when this method was called.
     */
    @Override
    public String toString() {
        return "ROTOR:\n" + "angle = " + angle + "\n ROTATING ABOUT " + rotorCenter + "\nDIRECTION OF ROTOR = " + direction;
    }//end method

    /**
     * CARE!!! The developer makes no guarantees about what will happen if an
     * invalid function string is passed to this method. This method only works
     * with valid functions that use the names assigned to the coordinate
     * axes(default is x,y,z). Wherever they occur as variables in the
     * expression, they MUST BE ENCLOSED IN CIRCULAR BRACKETS!!! THE VARIABLE
     * NAMES MUST BE SINGLE ALPHABET CHARACTERS ONLY!!!!. No parser or
     * expression analyzer is used before rotating this function, so be warned.
     *
     * IF YOUR FUNCTION WILL CONTAIN METHOD NAMES OR INBUILT FUNCTIONS THAT ARE
     * NOT LISTED IN THE {@link ROTOR#METHODS} ARRAY, ADD THEM TO THAT ARRAY
     *
     * @param function the function string to be rotated.
     * @return the rotated function.
     */
    public String rotate(String function) {
        // 1. Get the rotation expressions generated by your existing logic
        String rotXExpr = rotX();
        String rotYExpr = rotY();
        String rotZExpr = rotZ();

        Scanner sc = new Scanner(function, true, Method.getAllFunctions(), parameters, "(", ")");
        List<String> list = sc.scan();
       
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            String tk = list.get(i);
            String replacement = null;

            if (tk.equals(parameters[0])) {
                replacement = rotXExpr;
            } else if (tk.equals(parameters[1])) {
                replacement = rotYExpr;
            } else if (tk.equals(parameters[2])) {
                replacement = rotZExpr;
            }
            if (replacement != null) {
                // Wrapping in parentheses preserves the Order of Operations
                if (replacement.charAt(0) == '(' && replacement.charAt(replacement.length() - 1) == ')') { 
                    sb.append(replacement);
                } else {
                    sb.append("(").append(replacement).append(")");
                }
            } else {
                sb.append(tk);
            }
        }
        String str = sb.toString();
        if (str.charAt(0) == '(' && str.charAt(str.length() - 1) == ')') {
            str = str.substring(1, str.length()-1);
        } 
        return str;
    }
    

    /**
     * Converts a point in the mathematical xy plane to one in the Java screen
     * coordinates xy plane by rotating the point through PI radians in the
     * direction of the yz plane
     *
     * @param point a Point object in the mathematical xy plane
     * @return a Point object in the Java screen xy plane.
     */
    public static Point transformMathCoordsToJavaCoords(Point point) {
        return new ROTOR(PI, new Point(0, 0, 0), new Direction(1, 0, 0)).rotate(point);
    }//end method

    /**
     * Converts a point in the Java screen coordinates xy plane to one in the
     * mathematical xy plane by rotating the point through PI radians in the
     * direction of the yz plane
     *
     * @param point a Point object in the Java screen xy plane.
     * @return a Point object in the mathematical xy plane.
     */
    public static Point transformJavaCoordsToMathCoords(Point point) {
        return transformMathCoordsToJavaCoords(point);
    }//end method

    /**
     * @param line The line to be rotated.
     * @param ang The angle of rotation in rads.
     * @param p The point about which to rotate this Line object,
     * @return a new Line object after rotating it through theta rads about
     * point, p.
     */
    public static Line rotateLine(Line line, double ang, Point p) {
        double x1 = 0;
        double y1 = line.getY(x1);
        Point p1 = new Point(x1, y1);
        double y2 = 0;
        double x2 = line.getX(y2);

        Point p2 = new Point(x2, y2);

        Point p11 = ROTOR.planarXYRotate(p1, p, ang);
        Point p22 = ROTOR.planarXYRotate(p2, p, ang);

        return new Line(p11, p22);
    }

    /**
     * Rotates a Rectangle object.
     *
     * @param rect The Rectangle object to be rotated.
     * @param orbitalCenter The point about which rotation will occur.
     * @param angle The angle of rotation.
     * @return a Polygon object being the product of rotation of the Rectangle
     * object.
     */
    public static Polygon rotateRectangle(Rectangle rect, Point orbitalCenter, double angle) {
        Point cen = new Point(orbitalCenter.x, orbitalCenter.y);

        int width = rect.width;
        int height = rect.height;

        // Define vertices in a circular winding order
        Point topLeft = new Point(rect.x, rect.y);
        Point topRight = new Point(rect.x + width, rect.y);
        Point bottomRight = new Point(rect.x + width, rect.y + height);
        Point bottomLeft = new Point(rect.x, rect.y + height);

        // Rotate and convert to AWT points
        java.awt.Point p1 = planarXYRotate(topLeft, cen, angle).getAWTPoint();
        java.awt.Point p2 = planarXYRotate(topRight, cen, angle).getAWTPoint();
        // Swapped these two relative to your original version for correct winding
        java.awt.Point p3 = planarXYRotate(bottomRight, cen, angle).getAWTPoint();
        java.awt.Point p4 = planarXYRotate(bottomLeft, cen, angle).getAWTPoint();

        // Polygon automatically closes the loop between the last and first point
        int[] x = new int[]{p1.x, p2.x, p3.x, p4.x};
        int[] y = new int[]{p1.y, p2.y, p3.y, p4.y};

        return new Polygon(x, y, 4);
    }

    public static Polygon rotatePolygon(Polygon p, Point orbitalCenter, double angle) {
        int n = p.npoints; // The number of vertices in the original polygon
        int[] xRotated = new int[n];
        int[] yRotated = new int[n];

        // orbitalCenter is converted once to ensure compatibility with your Point class
        Point cen = new Point(orbitalCenter.x, orbitalCenter.y);

        for (int i = 0; i < n; i++) {
            // 1. Extract the current vertex as a gameMath.Point
            Point currentVertex = new Point(p.xpoints[i], p.ypoints[i]);

            // 2. Use your ROTOR API to perform the planar rotation
            Point rotatedVertex = planarXYRotate(currentVertex, cen, angle);

            // 3. Store the result in the arrays for the new Polygon
            java.awt.Point awtP = rotatedVertex.getAWTPoint();
            xRotated[i] = awtP.x;
            yRotated[i] = awtP.y;
        }

        // 4. Return the new transformed Polygon
        return new Polygon(xRotated, yRotated, n);
    }

    public static void main(String[] args) {
        MathExpression me = new MathExpression("rot(@(x)sin(x),pi/2,@(1,3)(0,0,0),@(1,3)(1,1,0))");
        Function f = FunctionManager.lookUp("anon1");
        System.out.println("scanner: " + me.getScanner());
        System.out.println("f = " + f.toString());
        System.out.println("ans:\n" + me.solve());

        me.setExpression("rot(@(1,3)(4,2,0),pi/2,@(1,3)(0,0,0),@(1,3)(1,1,0))");
        // Function f = FunctionManager.lookUp("anon1");
        // System.out.println("f = "+f.toString());
        System.out.println("ans:\n" + me.solve());
    }

    public static void main1(String args[]) {
        ROTOR rotor = new ROTOR(Math.PI, new Point(2, 3, 0), new Direction(1, 1, 1));
        // System.out.println( ROTOR.planarXYRotate( new Point(3,2,0),new Point(-5,1,0),PI/6   ) );
        //System.out.println( rotor.rotate( new Point(3,2,0)   ) );
        rotor.setAngle(Math.PI);
        rotor.setRotorCenter(new Point(1, 1, 0));
        System.out.println(rotor.rotate("sin(x)"));
        System.out.println(rotor.rotate("exp(x-3y)+4*z/y"));

    }//end method

}//end class

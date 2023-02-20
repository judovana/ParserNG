package math;


import parser.STRING;

import static java.lang.Math.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Maths {                       //3.14159265358979323846;
    public static BigDecimal PI =
            new BigDecimal("3.14159265358979323846264338327950288419716939937510582097494459230781640628620899862803482534211706798214808651");

    public static String PI() {
        return "3.1415926535897932";
    }

    @SuppressWarnings("empty-statement")
    public static String fact(String p) {
        String fact5 = "";
        Double i = 1d;
        Double prod = 1d;
        Double fact = 0d;
        Double dbVal = Double.parseDouble(p);
        Double n = Math.floor(dbVal);
        Double k = dbVal - n;
        Double d = 160 + k;


        if ((dbVal == 0) || (dbVal == 1.0)) {
            fact = 1.0;
        } else if ((dbVal < 0) && (k == 0)) {
            fact = Double.NEGATIVE_INFINITY;
        } else if ((dbVal <= d) && (k == 0)) {
            while (dbVal > 0) {
                prod *= dbVal;
                dbVal--;
            }//end while
            fact = prod;
        } else if ((dbVal <= d) && (k != 0)) {
            Double dSquare = d * d;
            Double dCube = dSquare * d;
            Double dFour = dCube * d;
            Double dFive = dFour * d;
            Double dSix = dFive * d;
            Double dSeven = dSix * d;
            Double dEight = dSeven * d;
            Double dNine = dEight * d;
            Double fact1 = Math.pow((d / Math.E), d) * Math.sqrt(2 * d * Math.PI) * (1 + (1 / (12 * d)) + (1 / (288 * dSquare)) - (139 / (51840 * dCube)) -
                    (59909 / (2.592E8 * dFour)) + (1208137 / (1.492992E9 * dFive)) - (1151957 / (1.875E11 * dSix)) - (101971 / (2.88E8 * dSeven)) +
                    (189401873 / (2.4E12 * dEight)) + (1293019 / (1.8E10 * dNine)));
            while ((n + i) <= 160) {
                prod *= (dbVal + i);
                i++;
            }
            fact = fact1 / prod;
        } else if ((dbVal > 160)) {
            Double dbValSquare = dbVal * dbVal;
            Double dbValCube = dbValSquare * dbVal;
            Double dbValFour = dbValCube * dbVal;
            Double dbValFive = dbValFour * dbVal;
            Double dbValSix = dbValFive * dbVal;
            Double dbValSeven = dbValSix * dbVal;
            Double dbValEight = dbValSeven * dbVal;
            Double dbValNine = dbValEight * dbVal;
            Double fact1 = Math.pow((dbVal / Math.E), dbVal) * Math.sqrt(2 * dbVal * Math.PI) * (1 + (1 / (12 * dbVal)) + (1 / (288 * dbValSquare)) - (139 / (51840 * dbValCube)) - (59909 / (2.592E8 * dbValFour)) + (1208137 / (1.492992E9 * dbValFive)) - (1151957 / (1.875E11 * dbValSix)) - (101971 / (2.88E8 * dbValSeven)) + (189401873 / (2.4E12 * dbValEight)) + (1293019 / (1.8E10 * dbValNine)));
            fact = fact1;

        }

        fact5 = String.valueOf(fact);
        return fact5;
    }//end method fact


    /**
     * method getExponent returns the power to which 10 is raised when the number is written in standard form
     * e.g an argument of 34.543 for the method gives a result of 1,since 34.543=3.4543*10^1
     *
     * @param num the Double number argument whose exponent is desire
     * @return the base 10 exponent of the number when written in standard form
     */
    public static int getExponent(Double num) {
        Double absVal = Math.abs(num);
        int count = 0;
        if (absVal < 1) {
            while (absVal < 1) {
                absVal *= 10;
                count++;
            }
            count *= -1;
        } else if (absVal > 10) {
            while (absVal > 10) {
                absVal /= 10;
                count++;
            }
        }
        return count;
    }//end method getExponent

    public static Double logToAnyBase(Double num, Double base) {
        return Math.log(num) / Math.log(base);
    }

    public static Double antiLogToAnyBase(Double num, Double base) {
        return Math.pow(base, num);
    }


    //returns the sign of a number string
    private static String sign(String num) {
        String sign = "";
        if (num.length() > 0) {
            if (num.substring(0, 1).equals("+") || num.substring(0, 1).equals("-")) {
                sign = num.substring(0, 1);
            } else {
                sign = "+";
            }
        }


        return sign;
    }

    private static boolean hasPoint(String num) {

        if (num.indexOf(".") >= 0) {
            return true;
        } else if (num.indexOf(".") < 0) {
            return false;
        }
        return false;
    }

    private static boolean hasExponent(String num) {

        if (num.indexOf("E") >= 0) {
            return true;
        } else if (num.indexOf("E") < 0) {
            return false;
        }
        return false;
    }

    private static boolean isNegNumber(String num) {
        return (sign(num).equals("-"));
    }

    //returns true if the character immediately after the E is a -
    private static boolean hasNegExponent(String num) {
        boolean yes = false;
        if (hasExponent(num) && num.substring(num.indexOf("E") + 1, num.indexOf("E") + 2).equals("-")) {
            yes = true;
        } else {
            yes = false;
        }
        return yes;
    }

    //the character immediately after the E is a +
    private static boolean hasPosExponent(String num) {
        boolean yes = false;
        if (hasExponent(num) && num.substring(num.indexOf("E") + 1, num.indexOf("E") + 2).equals("+")) {
            yes = true;
        } else {
            yes = false;
        }
        return yes;
    }

    //the character immediately after the E is a digit
    private static boolean hasUnsignedExp(String num) {
        boolean yes = false;

        if (hasExponent(num) && STRING.isDigit(num.substring(num.indexOf("E") + 1, num.indexOf("E") + 2))) {
            yes = true;
        } else {
            yes = false;
        }
        return yes;
    }

    private static String abs_val(String num) {
        if (isNegNumber(num)) {
            num = num.substring(1);
        } else {
            num += "";
        }
        return num;
    }

    //assumes that the input has a point
    private static String getNumbersBeforePoint(String num) {
        String thenumbers = "";
        if (hasPoint(num)) {
            thenumbers = num.substring(0, num.indexOf("."));
        } else if (!hasPoint(num)) {
            thenumbers = "";
        }
        return thenumbers;
    }

    //assumes that the input has a point
    private static String getNumbersAfterPoint(String num) {
        String thenumbers = "";
        if (hasExponent(num)) {
            thenumbers = num.substring(num.indexOf(".") + 1, num.indexOf("E"));
        } else if (!hasExponent(num)) {
            thenumbers = num.substring(num.indexOf(".") + 1);
        } else if (!hasPoint(num)) {
            thenumbers = "";
        }
        return thenumbers;
    }

    //assumes that the input has a point.It does not return the sign along with the string.
    private static String getNumbersAfterExp(String num) {
        String thenumbers = "";
        if (hasNegExponent(num) || hasPosExponent(num)) {
            thenumbers = num.substring(num.indexOf("E") + 2);
        } else if (hasUnsignedExp(num)) {
            thenumbers = num.substring(num.indexOf("E") + 1);
        } else if (!hasExponent(num)) {
            thenumbers = "0";
        }
        return thenumbers;
    }

    //creates a string of n zeroes
    private static String generateZeroes(int n) {
        String gen = "";
        for (int i = 0; i < n; i++) {
            gen += "0";
        }
        return gen;
    }

    //returns the number of leading zeroes in a given number string
//utility method for std_form.Helps to standardize numbers after the floating point but before the
//exponent when the numbers before the floating point=0
    private static int getNumberOfLeadingZeroes(String num) {
        int index = 0;
        for (int i = 0; i < num.length(); i++) {
            if (num.substring(i, i + 1).equals("0")) {
                index += 1;
            } else {
                break;
            }
        }
        return index;
    }

    //writes the number in a.bcde...E[+|-]abcde....e.g 1.432E45
    protected static String std_form(String num) {
        String sign = sign(num);//get the sign of the number
        String abs_val = abs_val(num);//get the absolute value of the number
        if (!hasPoint(abs_val) && !hasExponent(abs_val)) {
            abs_val += ".0E0";
        } else if (hasPoint(abs_val) && !hasExponent(abs_val)) {
            abs_val += "E0";
        } else if (!hasPoint(abs_val) && hasExponent(abs_val)) {
            abs_val = abs_val.substring(0, abs_val.indexOf("E")) + ".0" + abs_val.substring(abs_val.indexOf("E"));
        } else if (hasPoint(abs_val) && hasExponent(abs_val)) {
            abs_val += "";
        }

        String numbeforepoint = "";
        String the_std_form_of_num = "";
        long checkmethodgetNumbersBeforePoint = 0;
        try {
            checkmethodgetNumbersBeforePoint = Long.valueOf(getNumbersBeforePoint(abs_val));
        } catch (NumberFormatException numa) {
            checkmethodgetNumbersBeforePoint = 0;
        }

        if (checkmethodgetNumbersBeforePoint != 0) {
//this line gets the number preceding the point and at the same time removes all preceding zeroes in it.
//e.g 0034 becomes 34
            numbeforepoint = String.valueOf(checkmethodgetNumbersBeforePoint);
//this line gets the number preceding the E and at the same time removes all preceding zeroes in it.
//e.g 0034 becomes 34
            String numafterexp = String.valueOf(Long.valueOf(getNumbersAfterExp(abs_val)));
//rewrite number before point and after point but before the E ( if one exists ) in std_form e.g 4567=4.567E3
            String transform_num_before_exp_to_std_form =
                    numbeforepoint.substring(0, 1) + "." + numbeforepoint.substring(1) + getNumbersAfterPoint(abs_val) + "E" + (numbeforepoint.length() - 1);
            if (hasNegExponent(num)) {
                the_std_form_of_num = transform_num_before_exp_to_std_form.substring(0, transform_num_before_exp_to_std_form.indexOf("E")) + "E" +
                        +(-Long.valueOf(numafterexp) + Long.valueOf(transform_num_before_exp_to_std_form.substring(transform_num_before_exp_to_std_form.indexOf("E") + 1)));
            } else {
                the_std_form_of_num = transform_num_before_exp_to_std_form.substring(0, transform_num_before_exp_to_std_form.indexOf("E")) + "E" +
                        +(Long.valueOf(numafterexp) + Long.valueOf(transform_num_before_exp_to_std_form.substring(transform_num_before_exp_to_std_form.indexOf("E") + 1)));
            }

        } else if (checkmethodgetNumbersBeforePoint == 0) {

            //this line gets the number preceding the point and at the same time removes all preceding zeroes in it.
//e.g 0034 becomes 34
            numbeforepoint = "0";
            String numafterpoint = getNumbersAfterPoint(abs_val);
            String numafterpointwithoutleadingzeroes = String.valueOf(Long.valueOf(numafterpoint));
//this line gets the number preceding the E and at the same time removes all preceding zeroes in it.
//e.g 0034 becomes 34
            String numafterexp = String.valueOf(Long.valueOf(getNumbersAfterExp(abs_val)));
//rewrite number before point and after point but before the E ( if one exists ) in std_form e.g 4567=4.567E3


            String transform_num_before_exp_to_std_form =
                    numafterpointwithoutleadingzeroes.substring(0, 1) + "." + numafterpointwithoutleadingzeroes.substring(1) +
                            "0E" + "-" + (1 + getNumberOfLeadingZeroes(numafterpoint));


            if (hasNegExponent(num)) {
                the_std_form_of_num = transform_num_before_exp_to_std_form.substring(0, transform_num_before_exp_to_std_form.indexOf("E")) + "E" +
                        +(-Long.valueOf(numafterexp) + Long.valueOf(transform_num_before_exp_to_std_form.substring(transform_num_before_exp_to_std_form.indexOf("E") + 1)));
            } else {
                the_std_form_of_num = transform_num_before_exp_to_std_form.substring(0, transform_num_before_exp_to_std_form.indexOf("E")) + "E" +
                        +(Long.valueOf(numafterexp) + Long.valueOf(transform_num_before_exp_to_std_form.substring(transform_num_before_exp_to_std_form.indexOf("E") + 1)));
            }


        }


        String standardized = "";
        if (sign.equals("+")) {
            standardized = the_std_form_of_num;
        } else if (sign.equals("-")) {
            standardized = sign + the_std_form_of_num;
        }

        int j = standardized.indexOf("E");
        String modify1 = standardized.substring(0, j + 1);
        String modify2 = standardized.substring(j + 1);


        for (int i = j - 1; i >= 0; i--) {
            if (modify1.substring(i, i + 1).equals("0")) {
                modify1 = STRING.replace(modify1, "", i, i + 1);
            } else {
                break;
            }
        }


        return modify1 + modify2;
    }

    /**
     * @param num The number to examine
     * @return the number as a non-exponential number.
     */
    public static String non_exp_format(String num) {
        String result = "";//variable that stores the result

        if (hasExponent(num)) {

            String sign = sign(num);//get the sign of the number
            String abs_val = abs_val(num);//get the absolute value of the number
            String std_form = std_form(abs_val);//convert the input to its standard forme.g a.bcde...Eabcde....i.e1.2598E12 e.t.c
            String std_form1 = std_form;//store std_form in variable std_form1
            std_form1 = STRING.delete(std_form1, ".");//remove the floating point
            int L = std_form1.substring(0, std_form1.indexOf("E")).length();


            int expsize = Integer.valueOf(getNumbersAfterExp(std_form1));
            if (hasNegExponent(num)) {
                expsize *= -1;
            }


            if (expsize >= 0) {
                if (L > expsize) {
                    result = std_form1.substring(0, expsize + 1) +
                            "." + std_form1.substring(expsize + 1, std_form1.indexOf("E"));
                } else if (L <= expsize) {
                    result = std_form1.substring(0, std_form1.indexOf("E")) + generateZeroes(expsize - L + 1);
                }
            } else if (expsize < 0) {
                result = "0." + generateZeroes(Math.abs(expsize) - 1) + std_form1.substring(0, std_form1.indexOf("E"));
            }
            if (sign.equals("-")) {
                result = sign + result;
            } else {
                result += "";
            }


        }//end if
        else if (!hasExponent(num)) {
            result = num;
        }

        return result;
    }

    /**
     * method dec_to_other_base takes 2 arguments,the decimal number to be converted,
     * and the base to which the number is to be converted.
     * This method has the ability to convert the whole part of a decimal number to a specified base.
     *
     * @param dec_no  =the decimal number to be converted,
     * @param base_no =the base to which the number is to be converted.
     * @return the representation of the input decimal number in the specified base system.
     * @throws
     */
    private static String whole_dec_to_other_base(String dec_no, String base_no) {

        if (!dec_no.contains(".") && !base_no.contains(".")) {
            int decimalno = 0;
            int baseno = 0;
            String h;
            try {
                decimalno = Integer.parseInt(dec_no);
                baseno = Integer.parseInt(base_no);

                h = "";
                while (decimalno > 0) {
                    int rem = decimalno % baseno;
                    h += rem;
                    if ((rem == 0) && (rem < baseno)) {
                        decimalno /= baseno;
                    }//end if
                    else if ((rem > 0) && (rem < baseno)) {
                        decimalno = (decimalno - rem) / baseno;
                    }//end else if
                }//end for

                return STRING.reverse(h);
            } catch (NumberFormatException num) {
                System.out.println(dec_no);
                System.out.println(base_no);
                num.printStackTrace();
                throw new NumberFormatException("Only integers are expected here!");
            }

        }//end if
        else {
            throw new NumberFormatException("Only integers are expected here!");
        }


    }

    /**
     * method frac_dec_to_other_base takes 2 arguments,the decimal number to be converted,
     * and the base to which the number is to be converted. It however deals with positive decimals less than 1 and greater than 0.
     * This method has the ability to convert a decimal number to a specified base
     *
     * @param dec_no  =the decimal number to be converted,
     * @param base_no =the base to which the number is to be converted.
     * @return the representation of the input decimal number in the specified base system.
     * @throws
     */
    private static String frac_dec_to_other_base(String dec_no, String base_no) {
        String h = "";
        String j = "";
        Double f = 0d;
        Double f1 = 0d;
        try {
            f = Double.valueOf(dec_no);
            f1 = (double)Integer.parseInt(base_no);
            while (h.length() <= 18) {
                h += String.valueOf(f1 * f).substring(0, String.valueOf(f1 * f).indexOf("."));
                if (f1 * f * f1 < f1) {
                    f = f1 * f;
                } else if (f1 * f * f1 >= f1) {
                    f = Double.valueOf(String.valueOf(f1 * f).substring(String.valueOf(f1 * f).indexOf(".")));
                }
            }//end while
        }//end try
        catch (NullPointerException nola) {
            h = STRING.reverse("PLEASE ENTER A NUMBER");
        } catch (NumberFormatException no) {
            h = STRING.reverse("NUMBER TOO LARGE");
        } catch (IndexOutOfBoundsException ind) {
        }


        h = "." + h;

        int indexOfPoint = h.indexOf(".");
        if (indexOfPoint != -1) {
            int len = h.length();
            int i = len - 1;
            while (h.substring(i, i + 1).equals("0")) {
                i--;
            }
            h = h.substring(0, i + 1);
            if (h.endsWith(".")) {
                h = h + "0";
            }

        }


        return h;
    }

    /**
     * method dec_to_other_base takes 2 arguments,the decimal number to be converted,
     * and the base to which the number is to be converted.
     * This method has the ability to convert a decimal number to a specified base
     *
     * @param dec_no  =the decimal number to be converted,
     * @param base_no =the base to which the number is to be converted.
     * @return the representation of the input decimal number in the specified base system.
     */
    public static String dec_to_other_base(String dec_no, String base_no) {
        if (dec_no.contains("E") || dec_no.contains("Є")) {
            dec_no = Maths.non_exp_format(dec_no);//make the input number devoid of E
        }
        String v = "";
        int kk = 0;
        for (int i = 0; i < dec_no.length(); i++) {//record the incidence of floating points
            if (dec_no.substring(i, i + 1).equals(".")) {
                kk++;
                break;
            }
        }

        String nio = "";
        if (dec_no.substring(0, 1).equals("-")) {
            nio = "-";
            v = dec_no.substring(1);
        } else if (!dec_no.substring(0, 1).equals("-")) {
            nio = "";
            v = dec_no;
        }
        String r = "";
        String y = "";
        if (kk == 0) {//no point
            r = v;  //get the whole part of the number
            y = ".0";//get the decimal part of the number
        } else if (kk != 0) {//a point occurs
            r = v.substring(0, v.indexOf("."));//get the whole part of the number
            y = v.substring(v.indexOf("."));//get the decimal part of the number

        }

        String v1 = whole_dec_to_other_base(r, base_no);//the conversion of the whole part of the entry
        String v2 = frac_dec_to_other_base(y, base_no);//the conversion of the fractional part of the entry
        int u = 0;//variable that when equal to v2.length()-1 implies that v2 is equal to zero
        for (int i = 0; i < v2.length(); i++) {
            if (!v2.substring(i, i + 1).equals("0") && !v2.substring(i, i + 1).equals(".")) {
                v2 += "";
                break;
            } else {
                u++;
            }
        }

        if (u == v2.length()) {
            v2 = ".0";
        } else {
            v2 += "";
        }


// return nio+whole_dec_to_other_base(r, base_no)+frac_dec_to_other_base(y, base_no);
        return nio + v1 + v2;
    }

    /**
     * method num_to_base_10 takes 2 arguments,the number to be converted to base 10,
     * and the base system to which the number currently belongs.
     * This method has the ability to convert a number in a specified base back to
     * base 10 and so has the effect of reversing the action of method dec_to_other_base.
     *
     * @param num      is the number to be converted to base 10
     * @param num_base is the base to which the number currently belongs
     * @return the decimal representation of the input.
     */
    public static String num_to_base_10(String num, String num_base) {
        String h = "";//variable that will store the output of this method
        if (Integer.valueOf(num_base) <= 1) {
            throw new NumberFormatException("Invalid Number Base.");
        } else if (Integer.valueOf(num_base) > 1) {
            num = STRING.purifier(num);
            num_base = STRING.purifier(num_base);//remove all white spaces

            String nio = num;//store the original variable in nio
            // so that you can freely manipulate num to handle positive and negative numbers too
            int point_watch = -1;//if point_watch>-1 then the input contains a floating point,else
            //point_watch=-1 the input contains no floating point

            //The first if in the loop below checks if the digits in num are valid digits for the base system
            //of operation.If any digit in the input is equal to or greater than the base system,then the entry
            //is not a valid number under that base system so it instructs the software
            //to generate a NumberFormatException

            //The second if specifies the valid components of any number and checks all components of the input
            //to see if they are valid components.If any component of the input is not a valid number component,
            //The logic instructs the software to generate a NumberFormatException


            //The last if is only a floating point watchdog and checks to see if the input
            // contains a floating point or not.If a component of the number is a floating
            //point,the system documents or remembers this by incrementing variable point_watch
            for (int i = 0; i < num.length(); i++) {


                if (STRING.isDigit(num.substring(i, i + 1)) && Integer.valueOf(num.substring(i, i + 1)) >= Integer.valueOf(num_base)) {
                    num = "";
                    throw new NumberFormatException();
                }
                if (!num.substring(i, i + 1).equals(".") && !STRING.isDigit(num.substring(i, i + 1)) && !num.substring(i, i + 1).equals("-") && !num.substring(i, i + 1).equals("E")) {
                    num = "";
                    throw new NumberFormatException();
                }
                if (num.substring(i, i + 1).equals(".")) {
                    point_watch++;
                }
            }

            //The string v created below is a utility string for specifying what happens to our input under
            //various circumstances.
            //The if-else statement below gets the absolute value of the number and stores it in v.
            String v = "";
            if (num.substring(0, 1).equals("-")) {
                v = num.substring(1);
            } else {
                v = num;
            }

//Two strings r and y for storing the whole and the decimal part of the entry
            //under various circumstances
            String r = "";
            String y = "";
            try {
                int u = Integer.valueOf(num_base);
                if (point_watch == -1) {//no points
                    r = v;
                    y = "0";
                    r = STRING.reverse(r);
                } else if (point_watch != -1) {
                    r = v.substring(0, v.indexOf("."));//get the whole part of the number
                    y = v.substring(v.indexOf(".") + 1);//get the decimal part of the number
                    r = STRING.reverse(r);//This reverse of r is done so that we can loop easily later on
                    //from the beginning of r instead of from its end.
                }

                // i is a loop incrementer initialized to zero.H is used to accumulate calculations done within the loop.
                //The first loop uses H to accumulate its calculations while the second one below uses variable Ha to
                //accomplish the same purpose.H accumulates the whole part of the conversion,
                //while Ha accumulates the fractional part.
                int i = 0;
                Double H = 0d;

                while (i < r.length()) {//process the whole part
                    H += (Double.valueOf(r.substring(i, i + 1)) * Math.pow(u, i));
                    i++;
                }
                i = 0;

                Double Ha = 0d;

                while (i < y.length()) {//process the decimal part
                    Ha += (Double.valueOf(y.substring(i, i + 1)) * Math.pow(u, -i - 1));
                    i++;
                }
//Combine the 2 parts of the conversion to get the full conversion
                H += Ha;
//Call the original string and check if it was negative or positive
//and reflect this in the conversion.
                if (nio.substring(0, 1).equals("-")) {
                    h = "-" + String.valueOf(H);
                } else if (!nio.substring(0, 1).equals("-")) {
                    h = String.valueOf(H);
                }
            } catch (NullPointerException nol) {
                h = "SYNTAX ERROR at null";
            } catch (IndexOutOfBoundsException ind) {
                h = "SYNTAX ERROR at ind";
                ind.printStackTrace();
            } catch (NumberFormatException numa) {
                h = "SYNTAX ERROR at numa";
                numa.printStackTrace();
            }


        }
        return h;
    }

    /**
     * Method changeBase is designed to give flexibility in converting from one base to another
     * method changeBase takes 3 arguments,the number to be converted to base another base,
     * the base system to which the number currently belongs,and the base to which the number is to be converted
     * This method has the ability to convert a number in a specified base back to
     * base 10 and so has the effect of reversing the action of method dec_to_other_base.
     *
     * @param num      is the number to be converted to a new base
     * @param num_base is the base to which the number currently belongs
     * @param base     is the base to which the number is to be converted.
     * @return the decimal representation of the input.
     */
    public static String changeBase(String num, String num_base, String base) {
        String h = num_to_base_10(num, num_base);
        String val = dec_to_other_base(h, base);
        return val.startsWith(".") ? 0 + val : val;
    }

    /**
     * @param num1       The first number.
     * @param base1      The base system of the first number.
     * @param num2       The second number.
     * @param base2      The base system of the second number.
     * @param resultbase The base system of the result.
     * @return the sum of the 2 numbers in the
     * target base system.
     */
    public static String add(String num1, int base1, String num2, int base2, int resultbase) {
        String no1_to_base10 = changeBase(num1, String.valueOf(base1), "10");
        String no2_to_base10 = changeBase(num2, String.valueOf(base2), "10");
        return changeBase(String.valueOf(Double.parseDouble(no1_to_base10) + Double.parseDouble(no2_to_base10)),
                "10", String.valueOf(resultbase));
    }


    /**
     * @param num1       The first number.
     * @param base1      The base system of the first number.
     * @param num2       The second number.
     * @param base2      The base system of the second number.
     * @param resultbase The base system of the result.
     * @return the difference of the 2 numbers in the
     * target base system.
     */
    public static String subtract(String num1, int base1, String num2, int base2, int resultbase) {

        String no1_to_base10 = changeBase(num1, String.valueOf(base1), "10");
        String no2_to_base10 = changeBase(num2, String.valueOf(base2), "10");
        return changeBase(String.valueOf(Double.parseDouble(no1_to_base10) - Double.parseDouble(no2_to_base10)),
                "10", String.valueOf(resultbase));
    }

    /**
     * @param num1       The first number.
     * @param base1      The base system of the first number.
     * @param num2       The second number.
     * @param base2      The base system of the second number.
     * @param resultbase The base system of the result.
     * @return the division product of the 2 numbers in the
     * target base system.
     */
    public static String divide(String num1, int base1, String num2, int base2, int resultbase) {
        String no1_to_base10 = changeBase(num1, String.valueOf(base1), "10");
        String no2_to_base10 = changeBase(num2, String.valueOf(base2), "10");
        return changeBase(String.valueOf(Double.parseDouble(no1_to_base10) / Double.parseDouble(no2_to_base10)),
                "10", String.valueOf(resultbase));
    }

    /**
     * @param num1       The first number.
     * @param base1      The base system of the first number.
     * @param num2       The second number.
     * @param base2      The base system of the second number.
     * @param resultbase The base system of the result.
     * @return the product of the 2 numbers in the
     * target base system.
     */
    public static String multiply(String num1, int base1, String num2, int base2, int resultbase) {
        String no1_to_base10 = changeBase(num1, String.valueOf(base1), "10");
        String no2_to_base10 = changeBase(num2, String.valueOf(base2), "10");
        return changeBase(String.valueOf(Double.parseDouble(no1_to_base10) * Double.parseDouble(no2_to_base10)),
                "10", String.valueOf(resultbase));
    }


    /**
     * Method scanintoList is designed to scan a string of numbers separated by commas into a List
     * serves to separate the individual number objects in a number string
     *
     * @param s is the string of numbers separated by commas
     * @return the List of scanned numbers
     */
    public static List<String> scanintoList(String s) {//A string of numbers separated by commas
        s += ",";//append a , to the end so that the scanner will detect the last number in the string too
        String acc = "";//read in numbers into this string.
        int commacounter = 0;
        List<String> input = new ArrayList<String>();
        for (int i = 0; i < s.length(); i++) {
            acc += s.substring(i, i + 1);
            if (s.substring(i, i + 1).equals(",")) {
                acc = Maths.non_exp_format(acc);
                try {
                    input.add(STRING.delete(acc, ","));
                    acc = "";
                    ++commacounter;
                } catch (NumberFormatException num) {

                }

            }
        }
        return input;
    }


    public static Double degToRad(Double deg) {//from degrees to radians
        return deg * (Math.PI / 180.0);
    }

    public static Double radToDeg(Double rad) {//from rad to degrees
        return rad * (180.0 / Math.PI);
    }

    public static Double degToGrad(Double deg) {//from rad to degrees
        return (10 * deg / 9.0);
    }

    public static Double gradToDeg(Double grad) {//from grad to degrees
        return 0.9 * grad;
    }

    public static Double radToGrad(Double rad) {//from rad to grad
        return rad * (200.0 / Math.PI);
    }

    public static Double gradToRad(Double grad) {//from rad to degrees
        return grad * (Math.PI / 200.0);
    }


    /**
     * @param angRad the angle in rads
     * @return the sine of an angle in degrees
     */
    public static Double sinRadToDeg(Double angRad) {
        return sin(radToDeg(angRad));
    }

    /**
     * @param angDeg the angle in degs
     * @return the sine of an angle in rads
     */
    public static Double sinDegToRad(Double angDeg) {
        return sin(degToRad(angDeg));
    }

    /**
     * @param angRad the angle in rads
     * @return the sine of an angle in grad
     */
    public static Double sinRadToGrad(Double angRad) {
        return sin(radToGrad(angRad));
    }

    /**
     * @param angGrad the angle in grads
     * @return the sine of an angle in rad
     */
    public static Double sinGradToRad(Double angGrad) {
        return sin(gradToRad(angGrad));
    }

    /**
     * @param angGrad the angle in degs
     * @return the sine of an angle in grad
     */
    public static Double sinDegToGrad(Double angGrad) {
        return sin(degToGrad(angGrad));
    }

    /**
     * @param angDeg the angle in degs
     * @return the sine of an angle in grad
     */
    public static Double sinGradToDeg(Double angDeg) {
        return sin(gradToDeg(angDeg));
    }


    /**
     * @param angRad the angle in rads
     * @return the cosine of an angle in degrees
     */
    public static Double cosRadToDeg(Double angRad) {
        return cos(radToDeg(angRad));
    }

    /**
     * @param angDeg the angle in degs
     * @return the cosine of an angle in rads
     */
    public static Double cosDegToRad(Double angDeg) {
        return cos(degToRad(angDeg));
    }

    /**
     * @param angRad the angle in rads
     * @return the cosine of an angle in grad
     */
    public static Double cosRadToGrad(Double angRad) {
        return cos(radToGrad(angRad));
    }

    /**
     * @param angGrad the angle in grads
     * @return the cosine of an angle in rad
     */
    public static Double cosGradToRad(Double angGrad) {
        return cos(gradToRad(angGrad));
    }

    /**
     * @param angGrad the angle in degs
     * @return the cosine of an angle in grad
     */
    public static Double cosDegToGrad(Double angGrad) {
        return cos(degToGrad(angGrad));
    }

    /**
     * @param angDeg the angle in degs
     * @return the cosine of an angle in grad
     */
    public static Double cosGradToDeg(Double angDeg) {
        return cos(gradToDeg(angDeg));
    }


    /**
     * @param angRad the angle in rads
     * @return the tangent of an angle in degrees
     */
    public static Double tanRadToDeg(Double angRad) {
        return tan(radToDeg(angRad));
    }

    /**
     * @param angDeg the angle in degs
     * @return the tangent of an angle in rads
     */
    public static Double tanDegToRad(Double angDeg) {
        return tan(degToRad(angDeg));
    }

    /**
     * @param angRad the angle in rads
     * @return the tangent of an angle in grad
     */
    public static Double tanRadToGrad(Double angRad) {
        return tan(radToGrad(angRad));
    }

    /**
     * @param angGrad the angle in grads
     * @return the tangent of an angle in rad
     */
    public static Double tanGradToRad(Double angGrad) {
        return tan(gradToRad(angGrad));
    }

    /**
     * @param angGrad the angle in degs
     * @return the tangent of an angle in grad
     */
    public static Double tanDegToGrad(Double angGrad) {
        return tan(degToGrad(angGrad));
    }

    /**
     * @param angDeg the angle in degs
     * @return the tangent of an angle in grad
     */
    public static Double tanGradToDeg(Double angDeg) {
        return tan(gradToDeg(angDeg));
    }


    /**
     * @param angRad the angle in rads
     * @return the arctan of an angle in degrees
     */
    public static Double atanRadToDeg(Double angRad) {
        return radToDeg(atan(angRad));
    }

    /**
     * @param angRad the angle in rads
     * @return the arccos of an angle in degrees
     */
    public static Double acosRadToDeg(Double angRad) {
        return radToDeg(acos(angRad));
    }


    /**
     * @param angRad the angle in rads
     * @return the arcsine of an angle in degrees
     */
    public static Double asinRadToDeg(Double angRad) {
        return radToDeg(asin(angRad));
    }

    /**
     * @param angDeg the angle in rads
     * @return the arcsine of an angle in rads
     */
    public static Double asinDegToRad(Double angDeg) {
        return degToRad(asin(angDeg));
    }

    /**
     * @param angRad the angle in rads
     * @return the arcsine of an angle in grads
     */
    public static Double asinRadToGrad(Double angRad) {
        return radToGrad(asin(angRad));
    }

    /**
     * @param angGrad the angle in rads
     * @return the arcsine of an angle in rads
     */
    public static Double asinGradToRad(Double angGrad) {
        return gradToRad(asin(angGrad));
    }

    /**
     * @param angDeg the angle in rads
     * @return the arcsine of an angle in grads
     */
    public static Double asinDegToGrad(Double angDeg) {
        return (asinDegToGrad(angDeg));
    }

    /**
     * @param angGrad the angle in rads
     * @return the arcsine of an angle in degrees
     */
    public static Double asinGradToDeg(Double angGrad) {
        return gradToDeg(asin(angGrad));
    }


    /**
     * @param angDeg the angle in rads
     * @return the arccos of an angle in rads
     */
    public static Double acosDegToRad(Double angDeg) {
        return degToRad(acos(angDeg));
    }

    /**
     * @param angRad the angle in rads
     * @return the arccos of an angle in grads
     */
    public static Double acosRadToGrad(Double angRad) {
        return radToGrad(acos(angRad));
    }

    /**
     * @param angGrad the angle in rads
     * @return the arccos of an angle in rads
     */
    public static Double acosGradToRad(Double angGrad) {
        return gradToRad(acos(angGrad));
    }

    /**
     * @param angDeg the angle in rads
     * @return the arccos of an angle in grads
     */
    public static Double acosDegToGrad(Double angDeg) {
        return (acosDegToGrad(angDeg));
    }

    /**
     * @param angGrad the angle in rads
     * @return the arccos of an angle in degrees
     */
    public static Double acosGradToDeg(Double angGrad) {
        return gradToDeg(acos(angGrad));
    }


    /**
     * @param angDeg the angle in rads
     * @return the arctan of an angle in rads
     */
    public static Double atanDegToRad(Double angDeg) {
        return degToRad(atan(angDeg));
    }

    /**
     * @param angRad the angle in rads
     * @return the arctan of an angle in grads
     */
    public static Double atanRadToGrad(Double angRad) {
        return radToGrad(atan(angRad));
    }

    /**
     * @param angGrad the angle in rads
     * @return the arctan of an angle in rads
     */
    public static Double atanGradToRad(Double angGrad) {
        return gradToRad(atan(angGrad));
    }

    /**
     * @param angDeg the angle in rads
     * @return the arctan of an angle in grads
     */
    public static Double atanDegToGrad(Double angDeg) {
        return (atanDegToGrad(angDeg));
    }

    /**
     * @param angGrad the angle in rads
     * @return the arctan of an angle in degrees
     */
    public static Double atanGradToDeg(Double angGrad) {
        return gradToDeg(atan(angGrad));
    }


    /**
     * @param x The number.
     * @return the arc sinh of the number.
     */
    public static Double asinh(Double x) {
        return Math.log(x + Math.sqrt(x * x + 1));
    }

    /**
     * @param x The number.
     * @return the arc sinh of the number.
     */
    public static Double acosh(Double x) {
        return Math.log(x + Math.sqrt(x * x - 1));
    }

    /**
     * @param x The number.
     * @return the arc sinh of the number.
     */
    public static Double atanh(Double x) {
        return 0.5 * Math.log((1 + x) / (1 - x));
    }


    /**
     * @param x The number.
     * @return the arc csch of the number.
     */
    public static Double acsch(Double x) {
        return asinh(1.0 / x);
    }

    /**
     * @param x The number.
     * @return the arc csch of the number.
     */
    public static Double asech(Double x) {
        return acosh(1 / x);
    }

    /**
     * @param x The number.
     * @return the arc coth of the number.
     */
    public static Double acoth(Double x) {
        return atanh(1 / x);
    }

    /**
     * @param number   The number
     * @param exponent The power. May be integer or floating point.
     * @return The number parameter raised to the power of the exponent parameter.
     */
    public static Double power(Double number, Double exponent) {

        if (exponent == 0) {
            return 1.0;
        } else if (exponent < 0) {
            exponent = -exponent;//make the power +ve;
            if (number == 0) {
                return 0.0;
            } else if (number < 0) {
                if (exponent % 2 == 0 || exponent % 2 == 1) {//handle -ve integer powers of -ve numbers
                    return 1.0 / pow(number, exponent);
                }//end if
                else {//handle -ve floating point powers of -ve numbers.
                    return Double.NaN;// a -ve number cannot be raised to a floating point power.
                }//end else
            }//end else if
            else if (number > 0) {//handle -ve integer powers of +ve numbers.
                if (exponent % 2 == 0 || exponent % 2 == 1) {
                    return 1.0 / pow(number, exponent);
                }//end if
                else {//handle -ve floating point powers of +ve numbers.
                    return exp(-exponent * log(number));
                }//end else
            }//end else if
            return Double.NaN;
        }//end else if
        else {

            if (number == 0) { //return exp( exponent * log(number) );
                return 0.0;
            } else if (number < 0) {
                if (exponent % 2 == 0 || exponent % 2 == 1) {
                    return pow(number, exponent);
                }//end if
                else {//handle floating point powers of -ve numbers.
                    return Double.NaN;
                }//end else
            }//end else if
            else if (number > 0) {
                if (exponent % 2 == 0 || exponent % 2 == 1) {
                    return pow(number, exponent);
                }//end if
                else {//handle floating point powers of +ve numbers.
                    return exp(exponent * log(number));
                }//end else
            }//end else if

            return Double.NaN;
        }//end else

    }

    /**
     * DEVELOPED FOR THE J2ME PLATFORM!
     *
     * @param d The number
     * @return the cube root.
     */
    public static Double cbrt(Double d) {

        if (d > 0) {
            return power(d, (1 / 3.0));
        }//end if
        else {
            return -power(Math.abs(d), (1 / 3.0));
        }
    }//end method


    /**
     * Developed by JIBOYE Oluwagbemiro for
     * the J2ME platform where no proper method for calculating power exists.
     * It also works on the J2SE platform.
     * <p>
     * A very high speed method that finds the result when the first argument is raised to
     * the power of the second. But the power must always be
     * a whole number. The method could be as much as 4-5 times
     * faster than the Math.pow method of Java's Math class and should be used
     * whenever the power is non-fractional.
     *
     * @param number   The number to raise to a power.
     * @param exponent The power it is to be raised to.
     * @return the result of number raised to the power of exponent.
     */
    public static Double pow(Double number, Double exponent) {


        int pow = exponent.intValue();

        Double result = 1.0;

        int i = 0;
        while (i < pow) {
            result *= number;//System.err.println("PROC--pow: "+pow+", i= "+i);
            i++;
        }
        return result;


    }//end method

    /**
     * Developed by JIBOYE Oluwagbemiro Olaoluwa for
     * the J2ME platform where no proper method for calculating the
     * natural logarithm of a number exists.
     * It also works on the J2SE platform, but is about 5 times slower than the
     * its counterpart in class Math.
     *
     * @param x The number whose natural
     *          logarithm is needed.
     * @return the natural logarithm of the number using the
     * relation: log(x) = 2*(p+p^3/3+p^5/5+p^7/7+...) where p = (x-1)/(x+1)
     * <p>
     * <p>
     * <p>
     * This formula runs fastest at values of x very close to 1.
     * e.g: 0.9&lte;x&lte;1.1 ,the accuracy is also highest for this range.
     * <p>
     * For this reason the algorithm uses a repeated-square-root deduction
     * process to force all numbers to fall into the specified range before
     * applying the log-series formula above. The output of this step
     * is , say <code> m = log(reduced_sqrt)</code>It then retrieves the number of
     * times it used the square root to force the number into the range..
     * say this number is <code>n</code>, and finds <code>N=2^n</code>.
     * The log is then <code> m*N</code>
     */

    public static Double log(Double x) {
        if (x <= 0) {
            return Double.NaN;
        } else {
            Double count = 1d;
            while (Math.abs(x - 1) >= 0.1) {
                x = Math.sqrt(x);
                count++;
            }//end while loop.
            count = pow(2d, count - 1);
            Double sum = 0.0;
            Double lastSum = 1.0E-11;
            Double i = 1.0;
            Double xCalc = (x - 1) / (x + 1);
            for (; Math.abs(sum - lastSum) > 0; i += 2.0) {
                lastSum = sum;
                sum += (pow(xCalc, i) / i);
            }//end for loop.
            return 2 * sum * count;
        }//end else
    }//end method


    /**
     * Developed by JIBOYE Oluwagbemiro for
     * the J2ME platform where no proper method for calculating the
     * exponent of a number exists.
     * It runs on the J2SE platform, too and is only slightly slower than its
     * counterpart in class Math.
     * @param x The number whose exponent is needed.
     * @return the exponent of the number.
     */
    public static Double exp(Double x) {
        boolean isPos = x >= 0;
        x = Math.abs(x);

        if (x <= 2) {
            Double sum = 0.0;
            Double fact = 1.0;
            Double lastSum = 1.0E-11;
            Double i = 0d;

            while (Math.abs(sum - lastSum) > 0) {
                lastSum = sum;
                if (i == 0 || i == 1) {
                    fact = 1.0;
                } else {
                    fact *= i;
                }
                sum += (pow(x, i) / fact);
                i++;
            }//end while
            if (!isPos) {
                return 1.0 / sum;
            } else {
                return sum;
            }
        }//end if
        else {
            if (!isPos) {
                return 1 / pow(exp(x / 15.0), 15.0);
            } else {
                return pow(exp(x / 15.0), 15.0);
            }
        }//end else if


    }//end method


    /**
     * We have code that computes the arctangent very accurately.
     * The same principles applied for the arctan do not work well
     * for arcsin and arccos throughout the whole range -1&leq;x&leq;1.
     * So we use the relationship between arctan,arccos and arcsin
     * to compute them.
     * This is it:
     * arctan(x)=arccos(1/sqrt(1+x^2))=arcsin(x/sqrt(1+x^2))
     * So arctan(x)=arccos(p)=arcsin(q)
     * where p = 1/sqrt(1+x^2) and so x = sqrt(1-p^2)/p
     * and q = x/sqrt(1+x^2) and so x = q/sqrt(1-q^2)
     * <p>
     * So for example to compute arccos(0.5):
     * Then p = 0.5 and to use arctan(x) to compute it,
     * we convert p to x coordinates using x = sqrt(1-p^2)/p.
     * So x = sqrt(1-0.5^2)/0.5 = sqrt(3)
     * Then arccos(0.5) = arctan(sqrt(3))
     * <p>
     * For arcsin, the same thing applies.
     * <p>
     * So for example to compute arcsin(0.5):
     * Then q = 0.5 and to use arctan(x) to compute it,
     * we convert q to x coordinates using x = q/sqrt(1-q^2)
     * So x = 0.5/sqrt(1-0.5^2) = sqrt(3)
     * Then arcsin(0.5) = arctan(1/sqrt(3))
     *
     * @param q The number.
     * @return the arc sine of the number.
     */
    public static Double asin(Double q) {

        if (Math.abs(q) <= 1) {
            Double x = q / Math.sqrt(1 - (q * q));// Converting To Tangent Coords.
            return Maths.atan(x);
        }//end if

        throw new ArithmeticException("x = " + q + " does not lie between -1 and 1.");
    }//end method

    /**
     * We have code that computes the arctangent very accurately.
     * The same principles applied for the arctan do not work well
     * for arcsin and arccos throughout the whole range -1&leq;x&leq;1.
     * So we use the relationship between arctan,arccos and arcsin
     * to compute them.
     * This is it:
     * arctan(x)=arccos(1/sqrt(1+x^2))=arcsin(x/sqrt(1+x^2))
     * So arctan(x)=arccos(p)=arcsin(q)
     * where p = 1/sqrt(1+x^2) and so x = sqrt(1-p^2)/p
     * and q = x/sqrt(1+x^2) and so x = q/sqrt(1-q^2)
     * <p>
     * So for example to compute arccos(0.5):
     * Then p = 0.5 and to use arctan(x) to compute it,
     * we convert p to x coordinates using x = sqrt(1-p^2)/p.
     * So x = sqrt(1-0.5^2)/0.5 = sqrt(3)
     * Then arccos(0.5) = arctan(sqrt(3))
     * <p>
     * For arcsin, the same thing applies.
     * <p>
     * So for example to compute arcsin(0.5):
     * Then q = 0.5 and to use arctan(x) to compute it,
     * we convert q to x coordinates using x = q/sqrt(1-q^2)
     * So x = 0.5/sqrt(1-0.5^2) = sqrt(3)
     * Then arcsin(0.5) = arctan(1/sqrt(3))
     *
     * @param p The number.
     * @return the arc cosine of the number.
     */
    public static Double acos(Double p) {
        if (Math.abs(p) <= 1) {
            Double x = Math.sqrt(1 - (p * p)) / p;// Converting To Tangent Coords.
            return Maths.atan(x);
        }//end if

        throw new ArithmeticException("x = " + p + " does not lie between -1 and 1.");
    }


    /**
     * We use a = atan(x)....x = tan(a)
     * Then approx... a = x-x^3/3+x^5/5-x^7/7+x^9/9.....
     * Then use the approx value for a in the Newton-Raphson formula
     * to get a very accurate value for a.
     * i.e x_n1 = x_n - f(x_n)/f'(x_n)
     *
     * @param x
     * @return
     */
    public static Double atan(Double x) {
        boolean isPositive = true;
        if (x > 0) {
            isPositive = true;
        } else {
            isPositive = false;
            x = -x;
        }


        if (x > 1) {
            Double a = 0.0;//approx atan.
            Double x_inv = 1.0 / x;
            Double x_inv_squared = x_inv * x_inv;
            Double prod = 1.0;
            for (int i = 0; i < 10; i++) {
                if (i == 0) {
                    prod = x_inv;
                } else {
                    prod *= (x_inv_squared);
                    prod /= (2 * i + 1);
                }
                if (i % 2 == 0) {
                    a += prod;
                } else {
                    a -= prod;
                }
            }//end for loop

            Double x_n = a;
            Double x_n1 = 0d;

            while (true) {
                Double sec_n = 1 / Math.cos(x_n);
                Double tan_n = Math.tan(x_n);
                x_n1 = x_n - ((tan_n - x_inv) / (sec_n * sec_n));
                if (Math.abs(x_n1 - x_n) <= 1.0E-14) {
                    break;
                } else {
                    x_n = x_n1;
                }
            }//end while loop
            if (isPositive) {
                return (Math.PI / 2.0 - x_n1);
            } else {
                return -1 * (Math.PI / 2.0 - x_n1);
            }
        }//end if


        else {
            Double a = 0.0;//approx atan.
            Double x_squared = x * x;
            Double prod = 1.0;
            for (int i = 0; i < 10; i++) {
                if (i == 0) {
                    prod = x;
                } else {
                    prod *= (x_squared);
                    prod /= (2 * i + 1);
                }
                if (i % 2 == 0) {
                    a += prod;
                } else {
                    a -= prod;
                }
            }//end for loop


            Double x_n = a;
            Double x_n1 = 0d;

            while (true) {
                Double sec_n = 1 / Math.cos(x_n);
                Double tan_n = Math.tan(x_n);

                x_n1 = x_n - ((tan_n - x) / (sec_n * sec_n));

                if (Math.abs(x_n1 - x_n) <= 1.0E-14) {
                    break;
                } else {
                    x_n = x_n1;
                }
            }//end while loop
            if (isPositive) {
                return x_n1;
            } else {
                return -1 * x_n1;
            }
        }
    }//end method.

    public static void main(String args[]) {


    }
    /*We use the principle:
     * pi=(magic_whole_no)*(SUM(i^-n))^(1/n) < where i goes from 1 to infinity during summation >
     * to calculate pi.
     * The magic_whole_no principle states that for value of constant n above equal to 2,4,6,8,10
     * and partially 14,the ratio,(pi^n)/(SUM(i^-n))= a whole_no constant.
     * In the case of 14, the ratio is not a perfect whole no,but =whole no +0.5
     * Here are the magic_whole_no values for some values of n
     *
     *       n                magic_whole _no
     *       2                        6
     *       4                        90
     *       6                        945
     *       8                        9450
     *      10                        93555
     *      14                        9121612.5
     *The series converges faster for larger values of n.
     *Using n=14,the algorithm returns pi=3.141592653589793 after only 12 iterations
     *
     *
     *
     *
     *
     *
     *
     */

} //end class
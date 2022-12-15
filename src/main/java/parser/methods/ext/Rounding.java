package parser.methods.ext;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

import parser.TYPE;
import parser.methods.BasicNumericalMethod;
import parser.methods.Help;

public class Rounding {

    /* Implemented is only round, rundN, roundDigitsN and roundX ( 5 up)
     * Whole set is to add also  5 down):
     * round, roundDown
     * roundN, roundDownN
     * roundDigitsN, roundDigitsDown
     * roundX, roundDownX
     */

    public static class RoundN implements BasicNumericalMethod {

        @Override
        public String solve(List<String> tokens) {
            if (tokens.size() != 2) {
                throw new RuntimeException("roundN function takes exactly two argument");
            }
            int fractionalDigits = Utils.getFirstTokenAsInt(tokens);
            return naturalRound(fractionalDigits, tokens.get(1)).toString();
        }

        @Override
        public String getHelp() {
            return Help.toLine(getName(), "natural round of decimal number to another decimal by rounding to N fractional digits. eg roudN(4, 4.2585) -> 4.259");
        }

        @Override
        public String getName() {
            return "roundN";
        }

        @Override
        public String getType() {
            return TYPE.NUMBER.toString();
        }
    }

    public static class Round implements BasicNumericalMethod {

        @Override
        public String solve(List<String> tokens) {
            if (tokens.size() != 1) {
                throw new RuntimeException("round function takes exactly one argument");
            }
            return new RoundN().solve(Arrays.asList("0", tokens.get(0)));
        }

        @Override
        public String getHelp() {
            return Help.toLine(getName(), "natural round of decimal number to integer: 1.1->1, 1,4->1, 1.44->1, 1.45->1,  1.5->2, 1.9->2. It is same as calling roundN(0, ..)");
        }

        @Override
        public String getName() {
            return "round";
        }

        @Override
        public String getType() {
            return TYPE.NUMBER.toString();
        }
    }

    public static class RoundX implements BasicNumericalMethod {


        @Override
        public String solve(List<String> tokens) {
            if (tokens.size() != 2) {
                throw new RuntimeException("roundX function takes exactly two argument");
            }
            int fractionalDigits = Utils.getFirstTokenAsInt(tokens);
            return unnaturalRound(fractionalDigits, tokens.get(1)).toString();
        }

        @Override
        public String getHelp() {
            return Help.toLine(getName(), "unnatural round of decimal number to another decimal by rounding to N digits. eg roundX(1, 4.445)-> 4.5 unlike natural 4.4. 12.444 would remain natural.");
        }

        @Override
        public String getName() {
            return "roundX";
        }

        @Override
        public String getType() {
            return TYPE.NUMBER.toString();
        }

    }

    public static class RoundDigitsN implements BasicNumericalMethod {

        @Override
        public String solve(List<String> tokens) {
            if (tokens.size() != 2) {
                throw new RuntimeException("roundDigitsN function takes exactly two argument");
            }
            int digits = Utils.getFirstTokenAsInt(tokens);
            return new BigDecimal(tokens.get(1)).round(new MathContext(digits, RoundingMode.HALF_UP)).toString();
        }

        @Override
        public String getHelp() {
            return Help.toLine(getName(), "round the input to number of N digits roundDigitsN(2,125.134) -> 130");
        }

        @Override
        public String getName() {
            return "roundDigitsN";
        }

        @Override
        public String getType() {
            return TYPE.NUMBER.toString();
        }
    }

    static BigDecimal naturalRound(int origScale, String orig) {
        return naturalRound(origScale, new BigDecimal(orig));
    }

    static BigDecimal naturalRound(int origScale, BigDecimal orig) {
        if (origScale < 0) {
            throw new RuntimeException("Scale must be 0 or positive");
        }
        int[] l = Utils.decimalAndFractionalParts(orig);
        MathContext pr = new MathContext(l[0] + origScale, RoundingMode.HALF_UP);
        BigDecimal rounded = orig.round(pr);
        return rounded;
    }

    static BigDecimal unnaturalRound(int origScale, String orig) {
        return unnaturalRound(origScale, new BigDecimal(orig));
    }

    static BigDecimal unnaturalRound(int origScale, BigDecimal orig) {
        if (origScale < 0) {
            throw new RuntimeException("Scale must be 0 or positive");
        }
        BigDecimal rounded = orig;
        while (Utils.decimalAndFractionalParts(rounded)[1] > origScale) {
            int[] l = Utils.decimalAndFractionalParts(rounded);
            MathContext pr = new MathContext(l[0] + l[1] - 1, RoundingMode.HALF_UP);
            rounded = rounded.round(pr);
        }
        return rounded;
    }
}
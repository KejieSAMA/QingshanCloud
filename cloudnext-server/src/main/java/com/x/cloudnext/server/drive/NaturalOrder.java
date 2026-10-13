package com.x.cloudnext.server.drive;

import java.math.BigInteger;
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/** Shared, locale-aware natural ordering used before directory pagination. */
public final class NaturalOrder {

    private NaturalOrder() {
    }

    public static int compare(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        int ai = 0;
        int bi = 0;
        Collator collator = Collator.getInstance(Locale.CHINA);
        collator.setStrength(Collator.PRIMARY);
        while (ai < a.length() && bi < b.length()) {
            boolean aDigit = Character.isDigit(a.charAt(ai));
            boolean bDigit = Character.isDigit(b.charAt(bi));
            int ae = segmentEnd(a, ai, aDigit);
            int be = segmentEnd(b, bi, bDigit);
            String as = a.substring(ai, ae);
            String bs = b.substring(bi, be);
            int compared;
            if (aDigit && bDigit) {
                compared = new BigInteger(as).compareTo(new BigInteger(bs));
                if (compared == 0) {
                    compared = Integer.compare(as.length(), bs.length());
                }
            } else {
                compared = collator.compare(as, bs);
            }
            if (compared != 0) {
                return compared;
            }
            ai = ae;
            bi = be;
        }
        return Integer.compare(a.length(), b.length());
    }

    public static Comparator<String> comparator() {
        return NaturalOrder::compare;
    }

    private static int segmentEnd(String value, int start, boolean digit) {
        int index = start + 1;
        while (index < value.length() && Character.isDigit(value.charAt(index)) == digit) {
            index++;
        }
        return index;
    }
}

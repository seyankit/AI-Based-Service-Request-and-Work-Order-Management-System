package ph.edu.htcgsc.serviceportal.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class RegistrationValidator {
    private static final Pattern SCHOOL_EMAIL = Pattern.compile("^[A-Z0-9._%+-]+@online\\.htcgsc\\.edu\\.ph$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile("^[\\p{L} .'-]+$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern CONTACT = Pattern.compile("^(?:\\+63|0)9\\d{9}$");
    private static final Set<String> PERSONNEL_TYPES = Set.of("Faculty","Staff","Administrative Personnel","Department Personnel","Office Personnel","Service Personnel","Authorized School Personnel");
    private static final Set<String> SUFFIXES = Set.of("", "Jr.", "Sr.", "II", "III", "IV");
    private RegistrationValidator() {}
    public static String clean(String v) { return v == null ? "" : v.trim().replaceAll("\\s+", " "); }
    public static String normalizeEmail(String v) { return clean(v).toLowerCase(Locale.ROOT); }
    public static String normalizeContact(String v) { String c=clean(v).replaceAll("[\\s-]",""); return c.isEmpty()?null:c; }
    public static boolean validRequiredName(String v) { String c=clean(v); return c.length()<=70 && !c.isEmpty() && NAME.matcher(c).matches(); }
    public static boolean validOptionalName(String v) { String c=clean(v); return c.isEmpty() || (c.length()<=70 && NAME.matcher(c).matches()); }
    public static boolean validSuffix(String v) { return SUFFIXES.contains(clean(v)); }
    public static boolean validEmail(String v) { String e=normalizeEmail(v); return e.length()<=120 && SCHOOL_EMAIL.matcher(e).matches(); }
    public static boolean validContact(String v) { String c=normalizeContact(v); return c==null || CONTACT.matcher(c).matches(); }
    public static boolean validPersonnelType(String v) { return PERSONNEL_TYPES.contains(clean(v)); }
    public static boolean strongPassword(String p) {
        if (p==null || p.length()<8 || p.length()>72 || !p.equals(p.trim())) return false;
        boolean u=false,l=false,n=false,s=false;
        for(char ch:p.toCharArray()){ if(Character.isUpperCase(ch))u=true; else if(Character.isLowerCase(ch))l=true; else if(Character.isDigit(ch))n=true; else if(!Character.isWhitespace(ch))s=true; }
        return u&&l&&n&&s;
    }
}

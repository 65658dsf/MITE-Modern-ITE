package io.github.ningmeng123.modernite;
/** Agreed ITE calendar, independent of Minecraft initialization. @author NingMeng123 */
public final class ExtremeCalendar {
    private ExtremeCalendar() {}
    public static boolean blood(long day) { return day>0 && day!=128 && day%(day<128?32:16)==0; }
    public static long bloodCount(long day) { day=Math.max(0,day);return day<128?day/32:3+(day-128)/16; }
    public static long day(long time) { return Math.max(0,time)/24000+1; }
    public static int cap(long day,int chunks,boolean blood,boolean deep) {
        return cap(day,chunks,blood,deep,3D,1D);
    }
    public static int cap(long day,int chunks,boolean blood,boolean deep,double bloodMultiplier,double growthPerDay) {
        if(chunks<=0)return 0;
        double base=50L*Math.max(0,chunks)/128+Math.max(100D,Math.max(0L,day)*growthPerDay);
        double value=base*(blood?bloodMultiplier:1D);
        return (int)Math.min(Integer.MAX_VALUE,Math.max(0D,value));
    }
}

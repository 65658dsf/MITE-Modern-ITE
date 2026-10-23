package io.github.ningmeng123.modernite;
/** Agreed ITE calendar, independent of Minecraft initialization. @author NingMeng123 */
public final class ExtremeCalendar {
    private ExtremeCalendar() {}
    public static boolean blood(long day) { return day>0 && day!=128 && day%(day<128?32:16)==0; }
    public static long bloodCount(long day) { day=Math.max(0,day);return day<128?day/32:3+(day-128)/16; }
    public static long day(long time) { return Math.max(0,time)/24000+1; }
    /** ITE 2.0.28: add the displayed date after chunk scaling, then double the deep allowance. @author NingMeng123 */
    public static int referenceCap(long day,int chunks,boolean blood,boolean deep) {
        if(chunks<=0)return 0;
        long allowance=Math.min(Integer.MAX_VALUE,50L*chunks/(blood?128:256)
                +Math.min(Integer.MAX_VALUE,Math.max(0L,day)));
        return (int)Math.min(Integer.MAX_VALUE,allowance*(deep?2L:1L));
    }
    public static int cap(long day,int chunks,boolean blood,boolean deep) {
        return cap(day,chunks,blood,deep,3D,1D);
    }
    public static int cap(long day,int chunks,boolean blood,boolean deep,double bloodMultiplier,double growthPerDay) {
        return cap(day,chunks,blood,deep,bloodMultiplier,growthPerDay,50,100,0);
    }
    public static int cap(long day,int chunks,boolean blood,boolean deep,double bloodMultiplier,double growthPerDay,
                          int chunkBase,int dayMinimum,int dayMaximum) {
        if(chunks<=0)return 0;
        double growth=Math.max(0L,day)*growthPerDay;
        if(dayMaximum>0)growth=Math.min(growth,Math.max(dayMinimum,dayMaximum));
        double base=(long)chunkBase*Math.max(0,chunks)/128+Math.max(dayMinimum,growth);
        double value=base*(blood?bloodMultiplier:1D);
        return (int)Math.min(Integer.MAX_VALUE,Math.max(0D,value));
    }
}

package io.github.ningmeng123.modernite;
/** Agreed ITE calendar, independent of Minecraft initialization. @author NingMeng123 */
public final class ExtremeCalendar {
    private ExtremeCalendar() {}
    public static boolean blood(long day) { return day>0 && day!=128 && day%(day<128?32:16)==0; }
    public static long bloodCount(long day) { day=Math.max(0,day);return day<128?day/32:3+(day-128)/16; }
    public static long day(long time) { return Math.max(0,time)/24000+1; }
    public static int cap(long day,int chunks,boolean blood,boolean deep) {
        long base=50L*Math.max(0,chunks)/128+Math.max(100,day);
        long value=base*(blood?3L:1L)*(deep?2L:1L);
        return (int)Math.min(Integer.MAX_VALUE,Math.max(0,value));
    }
}

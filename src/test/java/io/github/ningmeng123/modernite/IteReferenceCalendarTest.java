package io.github.ningmeng123.modernite;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reference allowance semantics, including bounded arithmetic. @author NingMeng123 */
final class IteReferenceCalendarTest {
    @Test void datesAreAddedAfterChunkScalingAndDepthDoublesLast() {
        assertEquals(59,ExtremeCalendar.referenceCap(3,289,false,false));
        assertEquals(118,ExtremeCalendar.referenceCap(3,289,false,true));
        assertEquals(144,ExtremeCalendar.referenceCap(32,289,true,false));
        assertEquals(288,ExtremeCalendar.referenceCap(32,289,true,true));
        assertEquals(200,ExtremeCalendar.referenceCap(144,289,false,false));
        assertEquals(400,ExtremeCalendar.referenceCap(144,289,false,true));
    }
    @Test void thereIsNoHundredDayFloorOrWholeBudgetBloodMultiplier() {
        for(int day=1;day<=130;day++) {
            assertEquals(56+day,ExtremeCalendar.referenceCap(day,289,false,false));
            assertEquals(112+day,ExtremeCalendar.referenceCap(day,289,true,false));
        }
        assertEquals(32,ExtremeCalendar.referenceCap(32,1,false,false));
        assertEquals(0,ExtremeCalendar.referenceCap(32,0,true,true));
        assertEquals(0,ExtremeCalendar.referenceCap(32,-1,true,true));
    }
    @Test void extremeDatesAndAreasSaturateWithoutWrapping() {
        assertEquals(Integer.MAX_VALUE,ExtremeCalendar.referenceCap(Long.MAX_VALUE,Integer.MAX_VALUE,true,true));
        assertEquals(56,ExtremeCalendar.referenceCap(Long.MIN_VALUE,289,false,false));
    }
    @Test void editableDefaultsMatchTheSourceForEveryHeightAndSmallChunkRounding() {
        for(int chunks:new int[]{0,1,13,128,256,289,578})for(boolean blood:new boolean[]{false,true})for(boolean deep:new boolean[]{false,true})
            assertEquals(ExtremeCalendar.referenceCap(32,chunks,blood,deep),
                    ExtremeCalendar.configuredCap(32,chunks,blood,deep,2D,1D,50,0,0,2D));
        assertEquals(95,ExtremeCalendar.configuredCap(32,256,true,false,3D,2D,20,0,35,2D));
        assertEquals(190,ExtremeCalendar.configuredCap(32,256,true,true,3D,2D,20,0,35,2D));
        assertEquals(0,ExtremeCalendar.configuredCap(32,256,true,true,3D,2D,20,0,35,0D));
        assertEquals(Integer.MAX_VALUE,ExtremeCalendar.configuredCap(Long.MAX_VALUE,Integer.MAX_VALUE,true,true,1000D,10000D,1000000,0,0,1000D));
    }
}

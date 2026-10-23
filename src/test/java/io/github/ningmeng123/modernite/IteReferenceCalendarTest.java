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
}

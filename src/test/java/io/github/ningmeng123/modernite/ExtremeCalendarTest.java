package io.github.ningmeng123.modernite;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Calendar and budget boundaries from the approved design. @author NingMeng123 */
class ExtremeCalendarTest {
    @Test void eventsAndCounts() {
        assertTrue(ExtremeCalendar.blood(96));assertFalse(ExtremeCalendar.blood(128));assertFalse(ExtremeCalendar.blood(143));assertTrue(ExtremeCalendar.blood(144));assertTrue(ExtremeCalendar.blood(256));
        assertEquals(3,ExtremeCalendar.bloodCount(128));assertEquals(4,ExtremeCalendar.bloodCount(144));assertEquals(8,ExtremeCalendar.bloodCount(208));assertEquals(9,ExtremeCalendar.bloodCount(224));
        for(int day=1;day<3000;day++){long count=0;for(int i=1;i<=day;i++)if(ExtremeCalendar.blood(i))count++;assertEquals(count,ExtremeCalendar.bloodCount(day));}
    }
    @Test void sharedHeightBudgetRetainsDateGrowth() {
        for(boolean deep:new boolean[]{false,true}) {
            assertEquals(212,ExtremeCalendar.cap(1,289,false,deep));
            assertEquals(636,ExtremeCalendar.cap(32,289,true,deep));
            assertEquals(212,ExtremeCalendar.cap(100,289,false,deep));
            assertEquals(213,ExtremeCalendar.cap(101,289,false,deep));
            assertEquals(768,ExtremeCalendar.cap(144,289,true,deep));
            assertEquals(1112,ExtremeCalendar.cap(1000,289,false,deep));
            assertEquals(0,ExtremeCalendar.cap(1000,0,true,deep));
            assertEquals(0,ExtremeCalendar.cap(1,-1,false,deep));
        }
    }
    @Test void budgetsScaleByEligibleChunkUnionAndSaturateSafely() {
        assertEquals(150,ExtremeCalendar.cap(1,128,false,false));
        assertEquals(200,ExtremeCalendar.cap(1,256,false,false));
        assertEquals(212,ExtremeCalendar.cap(1,289,false,false));
        assertEquals(Integer.MAX_VALUE,ExtremeCalendar.cap(Long.MAX_VALUE,Integer.MAX_VALUE,true,true));
    }
    @Test void configurableBudgetsPreserveTheBaselineAndApplyBothControls() {
        assertEquals(636,ExtremeCalendar.cap(32,289,true,false,3D,1D));
        assertEquals(424,ExtremeCalendar.cap(32,289,true,false,2D,1D));
        assertEquals(512,ExtremeCalendar.cap(200,289,false,false,3D,2D));
        assertEquals(0,ExtremeCalendar.cap(200,289,true,false,0D,2D));
        assertEquals(212,ExtremeCalendar.cap(200,289,false,false,3D,0D));
    }
    @Test void configurableChunkAllowanceAndDateBoundsRemainIndependent() {
        assertEquals(290,ExtremeCalendar.cap(200,256,false,false,3D,2D,20,30,250));
        assertEquals(870,ExtremeCalendar.cap(200,256,true,true,3D,2D,20,30,250));
        assertEquals(440,ExtremeCalendar.cap(200,256,false,false,3D,2D,20,30,0));
        assertEquals(70,ExtremeCalendar.cap(1,256,false,false,3D,2D,20,30,10));
        assertEquals(0,ExtremeCalendar.cap(1000,289,false,false,3D,0D,0,0,0));
        assertEquals(0,ExtremeCalendar.cap(1000,0,true,false,3D,2D,20,30,250));
    }
}

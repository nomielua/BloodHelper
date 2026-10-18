package ru.bloodhelper.client.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HudLayoutTest {
    @Test void defaultLayoutKeepsItsProportionsAtLargeMinecraftGuiScales() {
        var music = HudLayout.place(480,270,214,94,1,.5,.035,"CENTER",false);
        var performance = HudLayout.place(480,270,118,101,1,.02,.035,"LEFT",false);
        var cooldown = HudLayout.place(480,270,160,67,1,.02,.35,"LEFT",false);
        var armor = HudLayout.place(480,270,160,66,1,.02,.63,"LEFT",false);
        var items = HudLayout.place(480,270,181,63,1,.5,.12,"CENTER",true);
        var notifications = HudLayout.place(480,270,214,137,1,.98,.78,"RIGHT",false);
        assertTrue(performance.x()+performance.width()<music.x());
        assertTrue(cooldown.y()+cooldown.height()<armor.y());
        assertTrue(items.x()+items.width()<notifications.x());
        assertEquals(.675,music.scale(),.0001);
        assertEquals(1,HudLayout.scale(640,400,214,94,1));
    }
    @Test void extremeUserPositionsAndScalesStillFitInsideViewport() {
        for(int width:new int[]{320,480,640})for(int height:new int[]{240,270,400}) {
            var p=HudLayout.place(width,height,214,200,2,2,-1,"RIGHT",true);
            assertTrue(p.x()>=0&&p.y()>=0&&p.x()+p.width()<=width&&p.y()+p.height()<=height);
        }
    }
}

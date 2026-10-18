package ru.bloodhelper.smoke;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import org.lwjgl.glfw.GLFW;
import ru.bloodhelper.client.BloodHelperClient;
import ru.bloodhelper.client.gui.*;
import ru.bloodhelper.client.hud.*;
import ru.bloodhelper.client.mixin.ItemCooldownsAccessor;
import ru.bloodhelper.client.mixin.CooldownInstanceAccessor;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real input events against the current rendered targets, in an isolated instance. */
public final class SmokeClient implements ClientModInitializer {
    private final Queue<Step> steps = new ArrayDeque<>();
    private boolean started, finished;
    private long renderedFrame, lastActionFrame = -1;
    private int wait;
    private volatile boolean capturing;
    private CompletableFuture<Void> reload;
    private Minecraft client;
    private record Step(int delay, Runnable action) {}
    @Override public void onInitializeClient() {
        BloodHelperClient.media().close(); BloodHelperClient.lyrics().close();
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> ScreenEvents.afterRender(screen)
                .register((s, graphics, mouseX, mouseY, delta) -> renderedFrame++));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if(finished)return;
            client=mc;
            if(mc.getOverlay()!=null)return;
            if(!started){if(!(mc.screen instanceof TitleScreen))return;started=true;plan();}
            if(capturing)return;
            if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;}
            if(wait-- > 0)return;
            // A catch-up tick burst can run several actions before one render. Always
            // let the screen paint its new hit targets before sending the next input.
            if(mc.screen!=null && renderedFrame==lastActionFrame)return;
            lastActionFrame=renderedFrame;
            Step step=steps.poll();
            if(step==null){finished=true;System.out.println("[BLOODHELPER_SMOKE] requested GUI/HUD/session/world smoke checks passed");mc.stop();return;}
            step.action.run();wait=step.delay;
        });
    }
    private void step(int ticks,Runnable action){steps.add(new Step(ticks,action));}
    private BloodHelperScreen menu(){return (BloodHelperScreen)client.screen;}
    private void plan(){
        if ("1".equals(System.getenv("BLOODHELPER_SMOKE_WORLD_ONLY"))) { planWorld(); return; }
        step(30,()->{checkAccessors();client.setScreen(new BloodHelperScreen(null));});
        step(2,()->{checkFonts();assertEightCards();shot("menu");});
        focusModule("music", "музыка");
        for(String mode:new String[]{"WORLD","BOTH","OFF","ISLAND"}){
            target("music.lyrics",()->click("music.lyrics"));
            step(3,()->{click("music.lyrics.option."+mode);if(!BloodHelperClient.config().music.lyricsMode.equals(mode))throw new AssertionError("Lyrics "+mode);});
        }
        step(3,()->{click("music.enabled");if(BloodHelperClient.config().music.enabled)throw new AssertionError("disable");});
        step(3,()->{click("music.enabled");if(!BloodHelperClient.config().music.enabled)throw new AssertionError("enable");});
        focusModule("session", "session");
        for(String mode:new String[]{"SMP","MEETUPS","FFA"}){
            target("session.mode",()->click("session.mode"));
            step(3,()->{click("session.mode.option."+mode);if(!BloodHelperClient.config().session.mode.equals(mode))throw new AssertionError("Session mode "+mode);});
            step(2,()->shot("session-"+mode.toLowerCase(Locale.ROOT)));
        }
        step(2,()->{if(find("session.kd")!=null||find("session.streak")!=null)throw new AssertionError("FFA vanilla-only fields");});
        target("session.reset",()->click("session.reset"));
        focusModule("armor", "armor");
        target("armor.mode",()->click("armor.mode"));
        step(3,()->{click("armor.mode.option.POINTS");if(!BloodHelperClient.config().armor.durabilityMode.equals("POINTS"))throw new AssertionError("Armor points");});
        step(2,()->shot("armor-settings"));
        focusModule("itemCounter", "item counter");
        target("itemCounter.items",()->click("itemCounter.items"));
        target("itemCounter.preset.windCharge",()->{click("itemCounter.preset.windCharge");if(!BloodHelperClient.config().itemCounter.items.contains("minecraft:wind_charge"))throw new AssertionError("Item selection");});
        step(2,()->shot("item-settings"));
        focusModule("notifications", "notifications");
        target("notifications.test",()->click("notifications.test"));
        step(12,()->{if(Notifications.snapshot().isEmpty())throw new AssertionError("Manual alert absent");});
        step(2,()->{if(Notifications.snapshot().isEmpty())throw new AssertionError("Manual alert lost between ticks");shot("notifications-test");});
        focusModule("performance", "performance");
        target("performance.graph",()->{click("performance.graph");if(BloodHelperClient.config().performance.showGraph)throw new AssertionError("Graph toggle");});
        target("performance.graph",()->click("performance.graph"));
        step(2,()->shot("performance-settings"));
        step(20,()->{click("nav.hud");if(!(client.screen instanceof HudEditorScreen))throw new AssertionError("HUD nav");});
        step(2,()->{assertVisible(client.screen.width,client.screen.height);shot("hud");});
        step(4,()->{
            var editor=(HudEditorScreen)client.screen;var box=HudRenderer.bounds().get("armor");
            double before=BloodHelperClient.config().armor.position.y;
            editor.mouseClicked(mouse(box.x()+10,box.y()+10),false);
            editor.mouseDragged(mouse(box.x()+10,box.y()+35),0,25);
            editor.mouseReleased(mouse(box.x()+10,box.y()+35));
            if(BloodHelperClient.config().armor.position.y<=before)throw new AssertionError("new widget drag");
        });
        step(3,()->{
            var box=HudRenderer.bounds().get("armor");double scale=BloodHelperClient.config().armor.position.scale;
            client.screen.mouseScrolled(box.x()+10,box.y()+10,0,1);
            if(BloodHelperClient.config().armor.position.scale<=scale)throw new AssertionError("new widget scale");
            client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_R,0,0));assertReset();
        });
        step(25,()->{client.getWindow().setWindowed(960,600);client.setScreen(new BloodHelperScreen(null));});
        step(2,()->{assertEightCards();shot("menu-small");});
        step(4,()->{click("search");type("музыка");});
        step(2,()->{
            if(find("cooldowns.enabled")!=null||find("pvp.enabled")!=null||find("music.enabled")==null)throw new AssertionError("Cyrillic search");
            shot("search-small");
        });
        step(3,()->{
            Object hit=find("search");clickLocal(number(hit,"x")+.5,number(hit,"y")+number(hit,"h")/2);
            GuiTextField input=(GuiTextField)field(menu(),"focused");
            if(input==null||!input.isFocused())throw new AssertionError("Field padding focus");
            menu().keyPressed(new KeyEvent(GLFW.GLFW_KEY_A,0,GLFW.GLFW_MOD_CONTROL));type("AV office Яё");
            if(!input.getValue().equals("AV office Яё"))throw new AssertionError("Replace selection");
            menu().keyPressed(new KeyEvent(GLFW.GLFW_KEY_HOME,0,0));menu().keyPressed(new KeyEvent(GLFW.GLFW_KEY_RIGHT,0,0));
            menu().keyPressed(new KeyEvent(GLFW.GLFW_KEY_RIGHT,0,GLFW.GLFW_MOD_SHIFT));
        });
        step(2,()->shot("font-input-selection"));
        step(3,()->{type("я");if(!((GuiTextField)field(menu(),"focused")).getValue().equals("Aя office Яё"))throw new AssertionError("Caret selection replacement");});
        step(20,()->client.setScreen(new HudEditorScreen(null)));step(2,()->{assertVisible(client.screen.width,client.screen.height);shot("hud-small");});
        step(25,()->{for(var w:HudWidgets.all(BloodHelperClient.config()))w.position().scale=2;client.getWindow().setWindowed(640,480);});
        step(2,()->{assertVisible(client.screen.width,client.screen.height);shot("hud-scale2");});
        step(5,()->{client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_R,0,0));assertReset();CustomFont.invalidate();reload=client.reloadResourcePacks();});
        step(20,()->{checkFonts();client.getWindow().setWindowed(1280,800);client.setScreen(new BloodHelperScreen(null));});
        step(2,()->shot("menu-reloaded"));
        step(25,()->client.getWindow().setWindowed(1920,1080));step(2,()->{assertMenuFits(menu());shot("menu-1080p");});
        step(25,()->{client.options.guiScale().set(1);client.resizeDisplay();});step(2,()->{assertMenuFits(menu());shot("menu-1080p-scale1");});
        step(25,()->{client.options.guiScale().set(2);client.resizeDisplay();client.getWindow().setWindowed(1280,800);client.setScreen(new BloodHelperScreen(null));});
        step(10,()->click("nav.appearance"));step(2,()->shot("appearance"));
        step(5,()->{slider("appearance.scale",0);near(BloodHelperClient.config().guiScale,.65,"scale minimum");});
        step(5,()->{slider("appearance.scale",1);near(BloodHelperClient.config().guiScale,1.05,"scale maximum");});
        step(5,()->{slider("appearance.scale",.875);near(BloodHelperClient.config().guiScale,1,"scale restore");});
        step(5,()->click("nav.profiles"));step(5,()->{click("profile.name");type("Визуал");});step(5,()->click("profile.save"));
        step(2,()->{if(!BloodHelperClient.profiles().contains("Визуал"))throw new AssertionError("profile save");shot("profiles");});
        step(5,()->{BloodHelperClient.config().music.lyricsMode="OFF";BloodHelperClient.config().session.mode="SMP";click("profile.load.Визуал");if(!BloodHelperClient.config().music.lyricsMode.equals("ISLAND")||!BloodHelperClient.config().session.mode.equals("FFA"))throw new AssertionError("profile load");});
        step(5,()->click("profile.delete.Визуал"));step(5,()->click("profile.delete.Визуал"));
        step(5,()->{if(BloodHelperClient.profiles().contains("Визуал"))throw new AssertionError("profile delete");});
        step(20,()->client.setScreen(new BloodHelperScreen(null)));step(2,()->{assertEightCards();shot("menu");});
        planWorld();
    }
    private void planWorld(){
        step(1,()->HudWorldFixture.start(client));
        await("world loading",()->HudWorldFixture.ready(client));
        step(1,()->HudWorldFixture.prepare(client));
        await("inventory synchronization",()->HudWorldFixture.prepared(client));
        step(30,()->HudWorldFixture.verify(client));
        step(2,()->shot("hud-live"));
        step(10,()->client.setScreen(new HudEditorScreen(null)));
        step(2,()->{assertVisible(client.screen.width,client.screen.height);shot("hud-world-preview");});
        step(4,()->{client.setScreen(null);});
        step(2,()->HudWorldFixture.finish(client));
    }
    private void await(String label,java.util.function.BooleanSupplier ready){step(1,()->waitFor(label,ready,System.nanoTime()+300_000_000_000L));}
    private void waitFor(String label,java.util.function.BooleanSupplier ready,long deadline){
        if(ready.getAsBoolean())return;
        if(System.nanoTime()>deadline)throw new AssertionError("Timeout: "+label);
        ((ArrayDeque<Step>)steps).addFirst(new Step(5,()->waitFor(label,ready,deadline)));
    }
    private void focusModule(String id,String query){
        step(5,()->{client.setScreen(new BloodHelperScreen(null));});
        step(5,()->{click("search");type(query);});
        step(5,()->click(id+".expand"));
    }
    private void target(String id,Runnable action){step(3,()->seek(id,action,0));}
    private void seek(String id,Runnable action,int tries){
        Object h=find(id);
        if(h!=null&&number(h,"y")>=68&&number(h,"y")+number(h,"h")<=474){action.run();return;}
        if(tries>=30)throw new AssertionError("Unreachable UI target "+id);
        scroll(-2);((ArrayDeque<Step>)steps).addFirst(new Step(3,()->seek(id,action,tries+1)));
    }
    private void assertEightCards(){for(String id:List.of("cooldowns","music","pvp","armor","itemCounter","notifications","performance","session"))if(find(id+".enabled")==null)throw new AssertionError("Missing card "+id);}
    private void shot(String suffix){capturing=true;Screenshot.grab(client.gameDirectory,"bloodhelper-"+suffix+".png",client.getMainRenderTarget(),1,m->{capturing=false;});}
    private void type(String value){for(int cp:value.codePoints().toArray())menu().charTyped(new CharacterEvent(cp,0));}
    private static void near(double a,double b,String message){if(Math.abs(a-b)>.001)throw new AssertionError(message+": "+a);}
    private Object find(String id){
        for(String list:new String[]{"hits","fields","sliders"})for(Object h:(List<?>)field(menu(),list))if(id.equals(field(h,"id")))return h;
        return null;
    }
    private void click(String id){Object h=find(id);if(h==null)throw new AssertionError("Missing UI target "+id);clickLocal(number(h,"x")+number(h,"w")/2,number(h,"y")+number(h,"h")/2);}
    private void slider(String id,double fraction){Object h=find(id);if(h==null)throw new AssertionError("Missing slider "+id+" on page "+field(menu(),"page")+" targets="+field(menu(),"sliders"));clickLocal(number(h,"x")+number(h,"w")*fraction,number(h,"y")+number(h,"h")/2);}
    private void clickLocal(double x,double y){var s=menu();double scale=number(s,"menuScale"),px=number(s,"originX")+x*scale,py=number(s,"originY")+y*scale;s.mouseClicked(mouse(px,py),false);s.mouseReleased(mouse(px,py));}
    private void scroll(double amount){var s=menu();double scale=number(s,"menuScale");s.mouseScrolled(number(s,"originX")+650*scale,number(s,"originY")+300*scale,0,amount);}
    private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static double number(Object o,String name){return ((Number)field(o,name)).doubleValue();}
    private static MouseButtonEvent mouse(double x,double y){return new MouseButtonEvent(x,y,new MouseButtonInfo(0,0));}
    private static void checkFonts(){
        for(var f:List.of(CustomFont.regular(),CustomFont.medium())){
            if(!f.sourceName().contains("SF Pro")||f.sourceGlyphCount()<500)throw new AssertionError("Wrong custom font: "+f.sourceName());
            for(String s:List.of("BLOODHELPER","Кириллица ЯяЁё","AV To 012345")){
                float[] carets=f.caretAdvances(s,14);
                if(carets.length!=s.length()+1||Math.abs(carets[s.length()]-f.width(s,14))>.001)throw new AssertionError("Shaped caret end");
                for(int i=1;i<carets.length;i++)if(carets[i]<carets[i-1])throw new AssertionError("Shaped caret order");
                if(f.width(s,14)<=0||Math.abs(f.width(s,28)-f.width(s,14)*2)>.01)throw new AssertionError("Custom metric scale");
                if(f.width(f.ellipsize(s,14,55),14)>55.01)throw new AssertionError("Custom ellipsis");
            }
        }
        System.out.println("[BLOODHELPER_SMOKE] actual OTF SF Pro Medium/Semibold, Cyrillic and custom metrics passed");
    }
    private static void assertMenuFits(BloodHelperScreen s){double scale=number(s,"menuScale"),w=BloodHelperScreen.MENU_WIDTH*scale,h=BloodHelperScreen.MENU_HEIGHT*scale,x=number(s,"originX"),y=number(s,"originY");if(x<0||y<0||x+w>s.width||y+h>s.height||w<s.width*.82||h<s.height*.82)throw new AssertionError("Viewport fit");}
    private static void assertVisible(int width, int height) {
        if (!HudRenderer.bounds().keySet().equals(java.util.Set.of("music", "cooldowns", "pvp", "armor", "itemCounter", "notifications", "performance", "session")))
            throw new AssertionError("Missing preview widget: " + HudRenderer.bounds().keySet());
        for (var box : HudRenderer.bounds().values()) {
            if (box.width() <= 0 || box.height() <= 0 || box.x() < 0 || box.y() < 0 || box.x() + box.width() > width || box.y() + box.height() > height)
                throw new AssertionError("HUD outside viewport: " + box);
        }
        // Inspect the actual hit targets; a click outside the widget proves nothing.
        try {
            var field = ru.bloodhelper.client.hud.MusicWidget.class.getDeclaredField("controls");
            field.setAccessible(true);
            if (!((java.util.Map<?, ?>)field.get(null)).isEmpty()) throw new AssertionError("Preview registered media control");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void assertReset() {
        var c = BloodHelperClient.config();
        var d = new ru.bloodhelper.client.config.ClientConfig();
        var actual = HudWidgets.all(c); var expected = HudWidgets.all(d);
        for (int i=0;i<actual.size();i++) {
            var a=actual.get(i);var e=expected.get(i);
            if(a.position().x!=e.position().x||a.position().y!=e.position().y||a.position().scale!=e.position().scale||!a.alignment().equals(e.alignment())||a.bottom()!=e.bottom())throw new AssertionError("HUD reset "+a.key());
        }
    }
    private static void checkAccessors() {
        ItemCooldowns manager = new ItemCooldowns();
        var group = Identifier.fromNamespaceAndPath("bloodhelper", "smoke_cooldown");
        manager.addCooldown(group, 20);
        for (int i = 0; i < 7; i++) manager.tick();
        var accessor = (ItemCooldownsAccessor)manager;
        var entry = (CooldownInstanceAccessor)accessor.bloodhelper$cooldowns().get(group);
        if (entry.bloodhelper$endTime() - accessor.bloodhelper$tickCount() != 13) throw new AssertionError("Cooldown remaining ticks");
        if (entry.bloodhelper$startTime() != 0) throw new AssertionError("Cooldown start tick");
        if (!manager.getCooldownGroup(new ItemStack(Items.ENDER_PEARL)).equals(Identifier.withDefaultNamespace("ender_pearl")))
            throw new AssertionError("Cooldown group");
        for (int i = 0; i < 13; i++) manager.tick();
        if (!accessor.bloodhelper$cooldowns().isEmpty()) throw new AssertionError("Cooldown expiry");
        System.out.println("[BLOODHELPER_SMOKE] live 1.21.11 cooldown accessor/group/expiry checks passed");
    }
}

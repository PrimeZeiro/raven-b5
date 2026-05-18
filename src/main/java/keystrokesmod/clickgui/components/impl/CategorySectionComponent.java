package keystrokesmod.clickgui.components.impl;



import keystrokesmod.clickgui.components.Component;

import keystrokesmod.module.Module;

import keystrokesmod.module.impl.client.Gui;

import keystrokesmod.utility.RenderUtils;

import keystrokesmod.utility.font.RavenFontRenderer;

import net.minecraft.client.renderer.GlStateManager;



import java.awt.*;



public class CategorySectionComponent extends Component {

    private static final float HEIGHT = 26.0f;

    private static final int BOX_BACKGROUND = new Color(36, 10, 62, 150).getRGB();

    private static final int BOX_BACKGROUND_HOVER = new Color(52, 16, 88, 175).getRGB();

    private static final int BOX_BORDER_START = new Color(180, 50, 255, 210).getRGB();

    private static final int BOX_BORDER_END = new Color(120, 40, 210, 140).getRGB();

    private static final int SECTION_ACCENT = new Color(200, 90, 255, 220).getRGB();

    private static final int SECTION_TEXT = new Color(235, 220, 255).getRGB();

    private static final int SECTION_TEXT_MUTED = new Color(175, 150, 210, 200).getRGB();



    public final Module.category category;

    public final CategoryComponent categoryComponent;

    public float yPos;



    public CategorySectionComponent(Module.category category, CategoryComponent categoryComponent) {

        this.category = category;

        this.categoryComponent = categoryComponent;

    }



    @Override

    public void updateHeight(float y) {

        this.yPos = y;

    }



    @Override

    public float getHeightF() {

        return HEIGHT;

    }



    @Override

    public void render() {

        float x = categoryComponent.getX();

        float y = categoryComponent.getY() + yPos;

        float width = categoryComponent.getWidth();

        float bottom = y + HEIGHT;

        boolean expanded = categoryComponent.isSectionExpanded();



        int bg = categoryComponent.sectionHeaderHovered ? BOX_BACKGROUND_HOVER : BOX_BACKGROUND;

        RenderUtils.drawRoundedRectangle(x, y, x + width, bottom, 5.0f, bg);

        RenderUtils.drawRoundedGradientOutlinedRectangle(x, y, x + width, bottom, 5.0f, 0x00000000,

                BOX_BORDER_START, BOX_BORDER_END);

        RenderUtils.drawRoundedRectangle(x + 3.0f, y + 5.0f, x + 5.0f, bottom - 5.0f, 1.0f, SECTION_ACCENT);



        CategoryComponent.renderCategoryIcon(category, (int) (x + 9.0f), (int) (y + 6.0f), true);



        RavenFontRenderer titleRenderer = Gui.getClickGuiHeaderFontRenderer();

        String label = formatCategoryName(category);

        titleRenderer.drawString(label, x + 26.0f, y + 6.0f, SECTION_TEXT, false);



        int moduleCount = categoryComponent.getModules().size();

        String hint = expanded ? "Click to collapse" : moduleCount + " module" + (moduleCount == 1 ? "" : "s");

        titleRenderer.drawString(hint, x + width - titleRenderer.getStringWidth(hint) - 22.0f, y + 8.0f,

                SECTION_TEXT_MUTED, false);



        String chevron = expanded ? "\u25BC" : "\u25B6";

        titleRenderer.drawString(chevron, x + width - 14.0f, y + 6.0f, SECTION_ACCENT, false);



        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

    }



    public boolean onClick(float mouseX, float mouseY, float scroll, int mouseButton) {

        if (mouseButton != 0 || !isMouseOver(mouseX, mouseY, scroll)) {

            return false;

        }

        categoryComponent.toggleSectionExpanded();

        return true;

    }



    public boolean isMouseOver(float mouseX, float mouseY, float scroll) {

        float x = categoryComponent.getX();

        float y = categoryComponent.getY() + yPos - scroll;

        float width = categoryComponent.getWidth();

        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEIGHT;

    }



    private static String formatCategoryName(Module.category category) {

        String name = category.name();

        if (name.isEmpty()) {

            return name;

        }

        return Character.toUpperCase(name.charAt(0)) + name.substring(1);

    }

}



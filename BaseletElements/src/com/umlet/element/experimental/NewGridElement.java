package com.umlet.element.experimental;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import com.baselet.control.NewGridElementConstants;
import com.baselet.control.enumerations.Direction;
import com.baselet.control.enumerations.LineType;
import com.baselet.diagram.draw.BaseDrawHandler;
import com.baselet.diagram.draw.geom.Dimension;
import com.baselet.diagram.draw.geom.DimensionDouble;
import com.baselet.diagram.draw.geom.Line;
import com.baselet.diagram.draw.geom.Point;
import com.baselet.diagram.draw.geom.PointDouble;
import com.baselet.diagram.draw.geom.Rectangle;
import com.baselet.diagram.draw.helper.ColorOwn;
import com.baselet.element.GridElement;
import com.baselet.element.StickingPolygon;
import com.baselet.element.StickingPolygon.StickLine;
import com.baselet.gui.AutocompletionText;
import com.umlet.element.experimental.element.uml.relation.Relation;
import com.umlet.element.experimental.facets.DefaultGlobalFacet.GlobalSetting;
import com.umlet.element.experimental.facets.DefaultGlobalTextFacet.ElementStyleEnum;
import com.umlet.element.experimental.facets.Facet;

public abstract class NewGridElement implements GridElement {

	//	private static final Logger log = Logger.getLogger(NewGridElement.class);

	private boolean stickingBorderActive;

	private BaseDrawHandler drawer; // this is the drawer for element specific stuff
	private BaseDrawHandler metaDrawer; // this is a separate drawer to draw stickingborder, selection-background etc.

	private GridElement group = null;

	private Properties properties;

	private Component component;

	private DrawHandlerInterface handler;

	private static final int MINIMAL_SIZE = NewGridElementConstants.DEFAULT_GRID_SIZE * 2;

	public void init(Rectangle bounds, String panelAttributes, String additionalAttributes, Component component, DrawHandlerInterface handler) {
		this.component = component;
		this.drawer = component.getDrawHandler();
		this.metaDrawer = component.getMetaDrawHandler();
		setRectangle(bounds);
		properties = new Properties(panelAttributes, drawer);
		setAdditionalAttributes(additionalAttributes);
		this.handler = handler;
	}

	public BaseDrawHandler getDrawer() {
		return drawer;
	}

	public BaseDrawHandler getMetaDrawer() {
		return metaDrawer;
	}

	@Override
	public String getPanelAttributes() {
		return properties.getPanelAttributes();
	}

	@Override
	public void setPanelAttributes(String panelAttributes) {
		properties.setPanelAttributes(panelAttributes);
		this.updateModelFromText();
	}

	@Override
	public void setGroup(GridElement group) {
		this.group = group;
	}

	@Override
	public GridElement CloneFromMe() {
		return handler.cloneElement();
	}

	/**
	 * ugly workaround to avoid that the Resize().execute() call which calls setSize() on this model updates the model during the
	 * calculated model update from autoresize. Otherwise the drawer cache would get messed up (it gets cleaned up 2 times in a row and afterwards everything gets drawn 2 times).
	 * Best testcase is an autoresize element with a background. Write some text and everytime autresize triggers, the background is drawn twice.
	 */
	private boolean autoresizePossiblyInProgress = false;

	@Override
	public void updateModelFromText() {
		this.autoresizePossiblyInProgress = true;
		drawer.clearCache();
		drawer.resetStyle(); // must be set before actions which depend on the fontsize (otherwise a changed fontsize would be recognized too late)
		properties.initSettingsFromText(this);
		updateMetaDrawer(metaDrawer);
		updateConcreteModel(drawer, properties);
		this.autoresizePossiblyInProgress = false;

		component.afterModelUpdate();
	}

	protected abstract void updateConcreteModel(BaseDrawHandler drawer, Properties properties);

	protected void updateMetaDrawer(BaseDrawHandler drawer) {
		drawer.clearCache();
		drawer.setForegroundColor(ColorOwn.TRANSPARENT);
		drawer.setBackgroundColor(ColorOwn.SELECTION_BG);
		drawer.drawRectangle(0, 0, getRealSize().width, getRealSize().height);
		drawer.resetColorSettings();
		if (NewGridElementConstants.show_stickingpolygon && !this.isPartOfGroup()) {
			drawStickingPolygon(drawer);
		}
	}

	@Override
	public void updateProperty(GlobalSetting key, String newValue) {
		properties.updateSetting(key, newValue);
		handler.updatePropertyPanel();
		//		this.getHandler().getDrawPanel().getSelector().updateSelectorInformation(); // update the property panel to display changed attributes
		updateModelFromText();
	}

	@Override
	public String getSetting(GlobalSetting key) {
		return properties.getSetting(key);
	}

	@Override
	public String getAdditionalAttributes() {
		return "";
	}

	@Override
	public void setAdditionalAttributes(String additionalAttributes) {
		// usually GridElements have no additional attributes
		/*TODO: perhaps refactor the additionattributes stuff completely (why should it be stored as a string? only for easier saving in uxf?)*/
	}

	@Override
	public boolean isPartOfGroup() {
		if (this.group != null) return true;
		return false;
	}

	@Override
	public boolean isInRange(Rectangle rect1) {
		return (rect1.contains(getRectangle()));
	}

	@Override
	public Set<Direction> getResizeArea(int x, int y) {
		Set<Direction> returnSet = new HashSet<Direction>();
		if (properties.getElementStyle() == ElementStyleEnum.NORESIZE || properties.getElementStyle() == ElementStyleEnum.AUTORESIZE) {
			return returnSet;
		}

		if ((x <= 5) && (x >= 0)) returnSet.add(Direction.LEFT);
		else if ((x <= this.getZoomedSize().width) && (x >= this.getZoomedSize().width - 5)) returnSet.add(Direction.RIGHT);

		if ((y <= 5) && (y >= 0)) returnSet.add(Direction.UP);
		else if ((y <= this.getZoomedSize().height) && (y >= this.getZoomedSize().height - 5)) returnSet.add(Direction.DOWN);
		return returnSet;
	}

	@Override
	public void setStickingBorderActive(boolean stickingBordersActive) {
		this.stickingBorderActive = stickingBordersActive;
	}

	@Override
	public boolean isStickingBorderActive() {
		return stickingBorderActive;
	}

	@Override
	public StickingPolygon generateStickingBorder(int x, int y, int width, int height) {
		StickingPolygon p = new StickingPolygon(x, y);
		p.addRectangle(0, 0, width, height);
		return p;
	}

	private StickingPolygon generateStickingBorder() {
		Rectangle r = getRectangle();
		return generateStickingBorder(r.x, r.y, r.width, r.height);
	}

	private final void drawStickingPolygon(BaseDrawHandler drawer) {
		StickingPolygon poly;
		// The Java Implementations in the displaceDrawingByOnePixel list start at (1,1) to draw while any others start at (0,0)
		if (handler.displaceDrawingByOnePixel()) poly = this.generateStickingBorder(1, 1, this.getRealSize().width - 1, this.getRealSize().height - 1);
		else poly = this.generateStickingBorder(0, 0, this.getRealSize().width - 1, this.getRealSize().height - 1);
		if (poly != null) {
			drawer.setLineType(LineType.DASHED);
			drawer.setForegroundColor(ColorOwn.STICKING_POLYGON);
			Vector<? extends Line> lines = poly.getStickLines();
			System.out.println(lines);
			drawer.drawLines(lines.toArray(new Line[lines.size()]));
			drawer.setLineType(LineType.SOLID);
			drawer.resetColorSettings();
		}
	}

	@Override
	public void changeSize(int diffx, int diffy) {
		this.setSize(this.getZoomedSize().width + diffx, this.getZoomedSize().height + diffy);
	}

	@Override
	public void setRectangle(Rectangle bounds) {
		component.setBoundsRect(bounds);
	}

	@Override
	public void setLocationDifference(int diffx, int diffy) {
		this.setLocation(this.getRectangle().x + diffx, this.getRectangle().y + diffy);
	}

	@Override
	public void setLocation(int x, int y) {
		Rectangle rect = getRectangle();
		rect.setLocation(x, y);
		component.setBoundsRect(rect);
	}

	@Override
	public void setSize(int width, int height) {
		if (width != getZoomedSize().width || height != getZoomedSize().height) { // only change size if it is really different
			Rectangle rect = getRectangle();
			rect.setSize(width, height);
			component.setBoundsRect(rect);
			if (!this.autoresizePossiblyInProgress) {
				updateModelFromText();
			}
		}
	}

	@Override
	public Rectangle getRectangle() {
		return component.getBoundsRect();
	}

	@Override
	public void repaint() {
		component.repaintComponent();
	}

	@Override
	public Dimension getZoomedSize() {
		Rectangle rect = getRectangle();
		return new Dimension(rect.getWidth(), rect.getHeight());
	}

	/**
	 * @see com.baselet.element.GridElement#getRealSize()
	 */
	@Override
	public Dimension getRealSize() {
		return new Dimension((int) (getZoomedSize().width / handler.getZoomFactor()), (int) (getZoomedSize().height / handler.getZoomFactor()));
	}

	@Override
	public Component getComponent() {
		return component;
	}

	private Settings settings;
	protected abstract Settings createSettings();
	public final Settings getSettings() {
		if (settings == null) {
			settings = createSettings();
		}
		return settings;
	}

	@Override
	public List<AutocompletionText> getAutocompletionList() {
		List<AutocompletionText> returnList = new ArrayList<AutocompletionText>();
		addAutocompletionTexts(returnList, getSettings().getGlobalFacets());
		addAutocompletionTexts(returnList, getSettings().getLocalFacets());
		return returnList;
	}

	private void addAutocompletionTexts(List<AutocompletionText> returnList, List<Facet> facets) {
		for (Facet f : facets) {
			for (AutocompletionText t : f.getAutocompletionStrings()) {
				t.setGlobal(f.isGlobal());
				returnList.add(t);
			}
		}
	}

	@Override
	public Integer getLayer() {
		return properties.getLayer();
	}

	public abstract ElementId getId();

	@Override
	public void handleAutoresize(DimensionDouble necessaryElementDimension) {
		double hSpaceLeftAndRight = drawer.getDistanceHorizontalBorderToText() * 2;
		double width = necessaryElementDimension.getWidth() + hSpaceLeftAndRight;
		double height = necessaryElementDimension.getHeight() + drawer.textHeight()/2;
		double diffw = width-getRealSize().width;
		double diffh = height-getRealSize().height;
		handler.Resize(diffw, diffh);
	}

	@Override
	public void drag(Collection<Direction> resizeDirection, int diffX, int diffY, Point mousePosBeforeDrag, boolean isShiftKeyDown, boolean firstDrag, Collection<? extends Stickable> stickables) {
		StickingPolygon oldStickingPolygon = generateStickingBorder();
		if (resizeDirection.isEmpty()) { // Move GridElement
			setLocationDifference(diffX, diffY);
		} else { // Resize GridElement
			Rectangle rect = component.getBoundsRect();
			if (isShiftKeyDown && diagonalResize(resizeDirection)) { // Proportional Resize
				if (diffX > diffY) diffX = diffY;
				if (diffY > diffX) diffY = diffX;
			}
			if (resizeDirection.contains(Direction.LEFT)) {
				rect.setX(rect.getX() + diffX);
				rect.setWidth(Math.max(rect.getWidth() - diffX, MINIMAL_SIZE));
			}
			if (resizeDirection.contains(Direction.RIGHT)) {
				rect.setWidth(Math.max(rect.getWidth() + diffX, MINIMAL_SIZE));
			}
			if (resizeDirection.contains(Direction.UP)) {
				rect.setY(rect.getY() + diffY);
				rect.setHeight(Math.max(rect.getHeight() - diffY, MINIMAL_SIZE));
			}
			if (resizeDirection.contains(Direction.DOWN)) {
				rect.setHeight(Math.max(rect.getHeight() + diffY, MINIMAL_SIZE));
			}
			updateModelFromText();
		}

		StickingPolygon newStickingPolygon = generateStickingBorder();

		// now check all stickinglines if they have changed and change sticking relations too
		Iterator<StickLine> oldLineIter = oldStickingPolygon.getStickLines().iterator();
		Iterator<StickLine> newLineIter = newStickingPolygon.getStickLines().iterator();
		while (oldLineIter.hasNext()) {
			StickLine oldLine = oldLineIter.next();
			StickLine newLine = newLineIter.next();
			System.out.println(oldLine);
			if (!oldLine.equals(newLine)) {

				for (Stickable stickable : stickables) {
					for (PointDouble pd : stickable.getStickablePoints()) {
						// the points are located relative to the upper left corner of the relation, therefore add this corner to have it located to the upper left corner of the diagram
						Point absolutePositionOfStickablePoint = new Point(stickable.getRectangle().getX() + (int) pd.x, stickable.getRectangle().getY() + (int) pd.y);
						if (oldLine.isConnected(absolutePositionOfStickablePoint, getGridSize())) {
							int stickLineDiffX = (int) (newLine.getCenter().getX()-oldLine.getCenter().getX());
							int stickLineDiffY = (int) (newLine.getCenter().getY()-oldLine.getCenter().getY());
							stickable.drag(resizeDirection, stickLineDiffX, stickLineDiffY, absolutePositionOfStickablePoint, isShiftKeyDown, true, Collections.<Relation>emptyList());
						}
					}
				}
				
			}
		}
		
	}

	@Override
	public void dragEnd() {
		// do nothing
	}

	@Override
	public boolean isSelectableOn(Point point) {
		return getRectangle().contains(point);
	}

	private boolean diagonalResize(Collection<Direction> resizeDirection) {
		return (resizeDirection.contains(Direction.UP) && resizeDirection.contains(Direction.RIGHT)) ||
				(resizeDirection.contains(Direction.UP) && resizeDirection.contains(Direction.LEFT)) ||
				(resizeDirection.contains(Direction.DOWN) && resizeDirection.contains(Direction.LEFT)) ||
				(resizeDirection.contains(Direction.DOWN) && resizeDirection.contains(Direction.RIGHT));
	}

	protected DrawHandlerInterface getHandler() {
		return handler;
	}

	public void onParsingStart() {
		// hook method, do nothing at default
	}

	public int getGridSize() {
		return (int) (getHandler().getZoomFactor() * NewGridElementConstants.DEFAULT_GRID_SIZE);
	}

}

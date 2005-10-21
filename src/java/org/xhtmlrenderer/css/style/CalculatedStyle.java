/*
 * CalculatedStyle.java
 * Copyright (c) 2004, 2005 Patrick Wright, Torbj�rn Gannholm
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package org.xhtmlrenderer.css.style;

import org.w3c.dom.css.CSSPrimitiveValue;
import org.w3c.dom.css.RGBColor;
import org.xhtmlrenderer.css.constants.CSSName;
import org.xhtmlrenderer.css.constants.IdentValue;
import org.xhtmlrenderer.css.constants.Idents;
import org.xhtmlrenderer.css.newmatch.CascadedStyle;
import org.xhtmlrenderer.css.sheet.PropertyDeclaration;
import org.xhtmlrenderer.css.style.derived.DerivedValueFactory;
import org.xhtmlrenderer.css.style.derived.RectPropertySet;
import org.xhtmlrenderer.css.value.Border;
import org.xhtmlrenderer.css.value.BorderColor;
import org.xhtmlrenderer.css.value.FontSpecification;
import org.xhtmlrenderer.util.XRLog;
import org.xhtmlrenderer.util.XRRuntimeException;

import java.awt.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;


/**
 * A set of properties that apply to a single Element, derived from all matched
 * properties following the rules for CSS cascade, inheritance, importance,
 * specificity and sequence. A derived style is just like a style but
 * (presumably) has additional information that allows relative properties to be
 * assigned values, e.g. font attributes. Property values are fully resolved
 * when this style is created. A property retrieved by name should always have
 * only one value in this class (e.g. one-one map). Any methods to retrieve
 * property values from an instance of this class require a valid {@link
 * org.xhtmlrenderer.layout.Context} be given to it, for some cases of property
 * resolution. Generally, a programmer will not use this class directly, but
 * will retrieve properties using a {@link org.xhtmlrenderer.context.StyleReference}
 * implementation.
 *
 * @author Torbj�rn Gannholm
 * @author Patrick Wright
 */
public class CalculatedStyle {
    private static final Map _cachedRects = new HashMap();

    /**
     * The parent-style we inherit from
     */
    private CalculatedStyle _parent;

    /**
     * A key for this style; names each of the properties defined in the style,
     * so is a "unique" identifier for the style, matching any other style
     * which have the same properties defined.
     */
    private String _styleKey;

    /**
     * Cache child styles of this style that have the same cascaded properties
     */
    private java.util.HashMap _childCache = new java.util.LinkedHashMap(5, 0.75f, true) {
        private static final int MAX_ENTRIES = 10;

        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    /**
     * Our main array of property values defined in this style, keyed
     * by the CSSName assigned ID.
     */
    private FSDerivedValue[] _derivedValuesById;

    /**
     * The derived border width for this RuleSet
     */
    private Border _drvBorderWidth;

    /**
     * The derived background color value for this RuleSet
     */
    private Color _drvBackgroundColor;

    /**
     * The derived border color value for this RuleSet
     */
    private BorderColor _drvBorderColor;

    /**
     * The derived Font for this style
     */
    private FontSpecification _font;


    /**
     * Default constructor; as the instance is immutable after use, don't use
     * this for class instantiation externally.
     */
    protected CalculatedStyle() {
        _derivedValuesById = new FSDerivedValue[CSSName.countCSSPrimitiveNames()];
    }


    /**
     * Constructor for the CalculatedStyle object. To get a derived style, use
     * the Styler objects getDerivedStyle which will cache styles
     *
     * @param parent  PARAM
     * @param matched PARAM
     */
    CalculatedStyle(CalculatedStyle parent, CascadedStyle matched) {
        this();
        _parent = parent;

        derive(matched);
        this._styleKey = genStyleKey();
    }

    /**
     * derives a child style from this style.
     * <p/>
     * depends on the ability to return the identical CascadedStyle each time a child style is needed
     *
     * @param matched the CascadedStyle to apply
     * @return The derived child style
     */
    public synchronized CalculatedStyle deriveStyle(CascadedStyle matched) {
        CalculatedStyle cs = (CalculatedStyle) _childCache.get(matched);

        if (cs == null) {
            cs = new CalculatedStyle(this, matched);
            _childCache.put(matched, cs);
        }
        return cs;
    }

    public int countAssigned() {
        int c = 0;
        for (int i = 0; i < _derivedValuesById.length; i++) {
            if (_derivedValuesById[i] != null) c++;
        }
        return c;
    }

    /**
     * Returns the parent style.
     *
     * @return Returns the parent style
     */
    public CalculatedStyle getParent() {
        return _parent;
    }

    /**
     * Converts to a String representation of the object.
     *
     * @return The borderWidth value
     */
    public String toString() {
        return _styleKey;
    }

    public float asFloat(CSSName cssName) {
        return valueByName(cssName).asFloat();
    }

    public String[] asStringArray(CSSName cssName) {
        return valueByName(cssName).asStringArray();
    }

    // TODO: doc
    public boolean hasAbsoluteUnit(CSSName cssName) {
        return valueByName(cssName).hasAbsoluteUnit();
    }

    /**
     * Gets the ident attribute of the CalculatedStyle object
     *
     * @param cssName PARAM
     * @param val     PARAM
     * @return The ident value
     */
    public boolean isIdent(CSSName cssName, IdentValue val) {
        return valueByName(cssName) == val;
    }

    /**
     * Gets the ident attribute of the CalculatedStyle object
     *
     * @param cssName PARAM
     * @return The ident value
     */
    public IdentValue getIdent(CSSName cssName) {
        return valueByName(cssName).asIdentValue();
    }

    /**
     * Convenience property accessor; returns a Color initialized with the
     * foreground color Uses the actual value (computed actual value) for this
     * element.
     *
     * @return The color value
     */
    public Color getColor() {
        return valueByName(CSSName.COLOR).asColor();
    }

    /**
     * Convenience property accessor; returns a Color initialized with the
     * background color value; Uses the actual value (computed actual value) for
     * this element.
     *
     * @return The backgroundColor value
     */
    public Color getBackgroundColor() {
        if (_drvBackgroundColor == null) {
            _drvBackgroundColor = valueByName(CSSName.BACKGROUND_COLOR).asColor();
            XRLog.cascade(Level.FINEST, "Background color: " + _drvBackgroundColor);
        }
        return _drvBackgroundColor;
    }

    /**
     * @param parentWidth
     * @param parentHeight
     * @param ctx
     * @return The "background-position" property as a Point
     */
    public Point getBackgroundPosition(float parentWidth, float parentHeight, CssContext ctx) {
        return valueByName(CSSName.BACKGROUND_POSITION).asPoint(CSSName.BACKGROUND_POSITION, parentWidth, parentHeight, ctx);
    }

    /**
     * Convenience property accessor; returns a BorderColor initialized with the
     * four-sided border color. Uses the actual value (computed actual value)
     * for this element.
     *
     * @return The borderColor value
     */
    public BorderColor getBorderColor() {
        if (_drvBorderColor == null) {
            BorderColor bcolor = new BorderColor();
            bcolor.topColor = valueByName(CSSName.BORDER_COLOR_TOP).asColor();
            bcolor.rightColor = valueByName(CSSName.BORDER_COLOR_RIGHT).asColor();
            bcolor.bottomColor = valueByName(CSSName.BORDER_COLOR_BOTTOM).asColor();
            bcolor.leftColor = valueByName(CSSName.BORDER_COLOR_LEFT).asColor();
            _drvBorderColor = bcolor;
        }
        return _drvBorderColor;
    }

    /**
     * Convenience property accessor; returns a Border initialized with the
     * four-sided border width. Uses the actual value (computed actual value)
     * for this element.
     *
     * @param ctx
     * @return The borderWidth value
     */
    public Border getBorderWidth(CssContext ctx) {
        if (_drvBorderWidth == null) {
            //note: percentages donot apply to border
            _drvBorderWidth = deriveBorderInstance(new CSSName[]{CSSName.BORDER_WIDTH_TOP, CSSName.BORDER_WIDTH_BOTTOM, CSSName.BORDER_WIDTH_LEFT, CSSName.BORDER_WIDTH_RIGHT},
                    0,
                    0, ctx);
            if (valueByName(CSSName.BORDER_STYLE_TOP) == IdentValue.NONE) {
                _drvBorderWidth.top = 0;
            }
            if (valueByName(CSSName.BORDER_STYLE_BOTTOM) == IdentValue.NONE) {
                _drvBorderWidth.bottom = 0;
            }
            if (valueByName(CSSName.BORDER_STYLE_LEFT) == IdentValue.NONE) {
                _drvBorderWidth.left = 0;
            }
            if (valueByName(CSSName.BORDER_STYLE_RIGHT) == IdentValue.NONE) {
                _drvBorderWidth.right = 0;
            }
        }
        return _drvBorderWidth;
    }

    public FontSpecification getFont(CssContext ctx) {
        if (_font == null) {
            _font = new FontSpecification();
            _font.size = getFloatPropertyProportionalTo(CSSName.FONT_SIZE, 0, ctx);

            _font.fontWeight = getIdent(CSSName.FONT_WEIGHT);
            _font.families = valueByName(CSSName.FONT_FAMILY).asStringArray();

            _font.fontStyle = getIdent(CSSName.FONT_STYLE);
            _font.variant = getIdent(CSSName.FONT_VARIANT);
        }
        return _font;
    }

    public float getFloatPropertyProportionalTo(CSSName cssName, float baseValue, CssContext ctx) {
        return valueByName(cssName).getFloatProportionalTo(cssName, baseValue, ctx);
    }

    /**
     * @param cssName
     * @param parentWidth
     * @param ctx
     * @return TODO
     */
    public float getFloatPropertyProportionalWidth(CSSName cssName, float parentWidth, CssContext ctx) {
        return valueByName(cssName).getFloatProportionalTo(cssName, parentWidth, ctx);
    }

    /**
     * @param cssName
     * @param parentHeight
     * @param ctx
     * @return TODO
     */
    public float getFloatPropertyProportionalHeight(CSSName cssName, float parentHeight, CssContext ctx) {
        return valueByName(cssName).getFloatProportionalTo(cssName, parentHeight, ctx);
    }

    /**
     * Convenience property accessor; returns a Border initialized with the
     * four-sided margin width. Uses the actual value (computed actual value)
     * for this element.
     *
     * @param parentWidth
     * @param parentHeight
     * @param ctx
     * @return The marginWidth value
     */
    public RectPropertySet getMarginRect(float parentWidth, float parentHeight, CssContext ctx) {
        return getRectProperty(this, CSSName.MARGIN_SHORTHAND, CSSName.MARGIN_SIDE_PROPERTIES, parentWidth, parentHeight, ctx);
    }

    /**
     * Convenience property accessor; returns a Border initialized with the
     * four-sided padding width. Uses the actual value (computed actual value)
     * for this element.
     *
     * @param parentWidth
     * @param parentHeight
     * @param ctx
     * @return The paddingWidth value
     */
    public RectPropertySet getPaddingRect(float parentWidth, float parentHeight, CssContext ctx) {
        return getRectProperty(this, CSSName.PADDING_SHORTHAND, CSSName.PADDING_SIDE_PROPERTIES, parentWidth, parentHeight, ctx);
    }

    /**
     * @param cssName
     * @return TODO
     */
    public String getStringProperty(CSSName cssName) {
        return valueByName(cssName).asString();
    }

    /**
     * Instantiates a Border instance for a four-sided property, e.g.
     * border-width, padding, margin.
     *
     * @param whichProperties Array of CSSNames for 4 sides, in order: top,
     *                        bottom, left, right
     * @param parentHeight    Container parent height
     * @param parentWidth     Container parent width
     * @param ctx
     * @return A Border instance representing the value for the
     *         4 sides.
     */
    private Border deriveBorderInstance(CSSName[] whichProperties, float parentHeight, float parentWidth, CssContext ctx) {
        Border border = new Border();
        border.top = (int) getFloatPropertyProportionalHeight(whichProperties[0], parentHeight, ctx);
        border.bottom = (int) getFloatPropertyProportionalHeight(whichProperties[1], parentHeight, ctx);
        border.left = (int) getFloatPropertyProportionalWidth(whichProperties[2], parentWidth, ctx);
        border.right = (int) getFloatPropertyProportionalWidth(whichProperties[3], parentWidth, ctx);
        return border;
    }

    /**
     * Returns a {@link FSDerivedValue} by name. Because we are a derived
     * style, the property will already be resolved at this point.
     *
     * @param cssName The CSS property name, e.g. "font-family"
     * @return See desc.
     */
    private FSDerivedValue valueByName(CSSName cssName) {
        FSDerivedValue val = _derivedValuesById[cssName.getAssignedID()];

        // but the property may not be defined for this Element
        if (val == null) {
            // if it is inheritable (like color) and we are not root, ask our parent
            // for the value
            if (CSSName.propertyInherits(cssName)
                    && _parent != null
                    //
                    && (val = _parent.valueByName(cssName)) != null) {

                val = val.copyOf(cssName);
            } else {
                // otherwise, use the initial value (defined by the CSS2 Spec)
                String initialValue = CSSName.initialValue(cssName);
                if (initialValue == null) {
                    throw new XRRuntimeException("Property '" + cssName + "' has no initial values assigned. " +
                            "Check CSSName declarations.");
                }
                if (initialValue.startsWith("=")) {
                    CSSName ref = CSSName.getByPropertyName(initialValue.substring(1));
                    val = valueByName(ref);
                } else {
                    initialValue = Idents.convertIdent(cssName, initialValue);

                    short type = guessType(initialValue);

                    val = DerivedValueFactory.newDerivedValue(
                            this,
                            cssName,
                            type,
                            initialValue,
                            initialValue,
                            null
                    );
                }
            }
            _derivedValuesById[cssName.getAssignedID()] = val;
        }
        return val;
    }

    /**
     * <p/>
     * <p/>
     * <p/>
     * <p/>
     * Implements cascade/inherit/important logic. This should result in the
     * element for this style having a value for *each and every* (visual)
     * property in the CSS2 spec. The implementation is based on the notion that
     * the matched styles are given to us in a perfectly sorted order, such that
     * properties appearing later in the rule-set always override properties
     * appearing earlier. It also assumes that all properties in the CSS2 spec
     * are defined somewhere across all the matched styles; for example, that
     * the full-property set is given in the user-agent CSS that is always
     * loaded with styles. The current implementation makes no attempt to check
     * either of these assumptions. When this method exits, the derived property
     * list for this class will be populated with the properties defined for
     * this element, properly cascaded.</p>
     *
     * @param matched PARAM
     */
    private void derive(CascadedStyle matched) {
        if (matched == null) {
            return;
        }//nothing to derive

        Iterator mProps = matched.getCascadedPropertyDeclarations();
        while (mProps.hasNext()) {
            PropertyDeclaration pd = (PropertyDeclaration) mProps.next();
            FSDerivedValue val = deriveValue(pd.getCSSName(), pd.getValue());
            _derivedValuesById[pd.getCSSName().getAssignedID()] = val;
        }
    }

    private FSDerivedValue deriveValue(CSSName cssName, org.w3c.dom.css.CSSPrimitiveValue value) {
        // Start assuming our computed value is the same as the specified value
        RGBColor rgb = (value.getPrimitiveType() == CSSPrimitiveValue.CSS_RGBCOLOR ? value.getRGBColorValue() : null);
        String s = (value.getPrimitiveType() == CSSPrimitiveValue.CSS_STRING ? value.getStringValue() : null);

        FSDerivedValue dval = DerivedValueFactory.newDerivedValue(
                this,
                cssName,
                value.getPrimitiveType(),
                value.getCssText(),
                s,
                rgb
        );
        FSDerivedValue cval = dval;

        //whats the point? (tobe) if (!specified.hasAbsoluteUnit()) {
        // inherit the value from parent element if value is set to inherit
        if (dval.isDeclaredInherit()) {
            // if we are root, have no parent, use the initial value as
            // defined by the CSS2 spec
            if (_parent == null) {
                throw new XRRuntimeException("CalculatedStyle: trying to resolve an inherited property, " +
                        "but have no parent CalculatedStyle (root of document?)--" +
                        "property '" + cssName + "' may not be defined in CSS.");
            } else {
                // pull from our parent CalculatedStyle
                cval = _parent.valueByName(cssName).copyOf(cssName);
            }
        }
        return cval;
    }

    private String genStyleKey() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < _derivedValuesById.length; i++) {
            CSSName name = CSSName.getByID(i);
            FSDerivedValue val = _derivedValuesById[i];
            if (val != null) {
                sb.append(name.toString());
            } else {
                sb.append("(no prop assigned in this pos)");
            }
            sb.append("|\n");
        }
        return sb.toString();

    }

// Incomplete routine to try and determine the
// CSSPrimitiveValue short code for a given value,
// e.g. 14pt is CSS_PT.

    /**
     * Description of the Method
     *
     * @param value PARAM
     * @return Returns
     */
    private static short guessType(String value) {
        short type = CSSPrimitiveValue.CSS_STRING;
        if (value != null && value.length() > 1) {
            if (value.endsWith("%")) {
                type = CSSPrimitiveValue.CSS_PERCENTAGE;
            } else if (value.startsWith("rgb") || value.startsWith("#")) {
                type = CSSPrimitiveValue.CSS_RGBCOLOR;
            } else {
                String hmm = value.substring(value.length() - 2);
                if ("pt".equals(hmm)) {
                    type = CSSPrimitiveValue.CSS_PT;
                } else if ("px".equals(hmm)) {
                    type = CSSPrimitiveValue.CSS_PX;
                } else if ("em".equals(hmm)) {
                    type = CSSPrimitiveValue.CSS_EMS;
                } else if ("ex".equals(hmm)) {
                    type = CSSPrimitiveValue.CSS_EXS;
                } else if ("in".equals(hmm)) {
                    type = CSSPrimitiveValue.CSS_IN;
                } else if ("cm".equals(hmm)) {
                    type = CSSPrimitiveValue.CSS_CM;
                } else if ("mm".equals(hmm)) {
                    type = CSSPrimitiveValue.CSS_MM;
                } else {
                    try {
                        new Float(value);
                        type = CSSPrimitiveValue.CSS_NUMBER;
                    } catch (NumberFormatException ex) {
                        type = CSSPrimitiveValue.CSS_STRING;
                    }
                }
            }
        }
        return type;
    }

    public static RectPropertySet getRectProperty(
            CalculatedStyle style,
            CSSName shorthandProp,
            CSSName[] sides,
            float parentWidth,
            float parentHeight,
            CssContext ctx
    ) {
        RectPropertySet rect = null;
        String key = null;
        if ((key = RectPropertySet.deriveKey(
                style,
                sides
        )) == null) {
            rect = newRectInstance(style, shorthandProp, sides, parentHeight, parentWidth, ctx);
        } else {
            rect = (RectPropertySet) _cachedRects.get(key);
            if (rect == null) {
                rect = newRectInstance(style, shorthandProp, sides, parentHeight, parentWidth, ctx);
                _cachedRects.put(key, rect);
            }
        }

        return rect;
    }

    private static RectPropertySet newRectInstance(
            CalculatedStyle style,
            CSSName shorthand,
            CSSName[] sides,
            float parentHeight,
            float parentWidth,
            CssContext ctx
    ) {
        RectPropertySet rect;
        rect = RectPropertySet.newInstance(
                style,
                shorthand,
                sides,
                parentHeight,
                parentWidth,
                ctx
        );
        return rect;
    }

}// end class

/*
 * $Id$
 *
 * $Log$
 * Revision 1.36  2005/10/21 13:02:20  pdoubleya
 * Changed to cache padding in RectPropertySet.
 *
 * Revision 1.35  2005/10/21 12:20:04  pdoubleya
 * Added array for margin side props.
 *
 * Revision 1.34  2005/10/21 12:16:18  pdoubleya
 * Removed use of MarginPropertySet; using RectPS  now.
 *
 * Revision 1.33  2005/10/21 12:01:13  pdoubleya
 * Added cachable rect property for margin, cleanup minor in styling.
 *
 * Revision 1.32  2005/10/21 10:02:54  pdoubleya
 * Cleanup, removed unneeded vars, reorg code in CS.
 *
 * Revision 1.31  2005/10/20 20:48:01  pdoubleya
 * Updates for refactoring to style classes. CalculatedStyle now has lookup methods to cover all general cases, so propertyByName() is private, which means the backing classes for styling were able to be replaced.
 *
 * Revision 1.30  2005/10/03 23:44:43  tobega
 * thread-safer css code and improved style caching
 *
 * Revision 1.29  2005/09/11 20:43:15  tobega
 * Fixed table-css interaction bug, colspan now works again
 *
 * Revision 1.28  2005/07/20 22:47:33  joshy
 * fix for 94, percentage for top absolute position
 *
 * Revision 1.27  2005/06/22 23:48:41  tobega
 * Refactored the css package to allow a clean separation from the core.
 *
 * Revision 1.26  2005/06/21 08:23:13  pdoubleya
 * Added specific list and count of primitive, non shorthand properties, and CalculatedStyle now sizes array to this size.
 *
 * Revision 1.25  2005/06/16 07:24:46  tobega
 * Fixed background image bug.
 * Caching images in browser.
 * Enhanced LinkListener.
 * Some house-cleaning, playing with Idea's code inspection utility.
 *
 * Revision 1.24  2005/06/03 23:06:21  tobega
 * Now uses value of "color" as initial value for "border-color" and rgb-triples are supported
 *
 * Revision 1.23  2005/06/01 00:47:02  tobega
 * Partly confused hack trying to get width and height working properly for replaced elements.
 *
 * Revision 1.22  2005/05/29 16:38:58  tobega
 * Handling of ex values should now be working well. Handling of em values improved. Is it correct?
 * Also started defining dividing responsibilities between Context and RenderingContext.
 *
 * Revision 1.21  2005/05/13 11:49:57  tobega
 * Started to fix up borders on inlines. Got caught up in refactoring.
 * Boxes shouldn't cache borders and stuff unless necessary. Started to remove unnecessary references.
 * Hover is not working completely well now, might get better when I'm done.
 *
 * Revision 1.20  2005/05/09 20:35:38  tobega
 * Caching fonts in CalculatedStyle
 *
 * Revision 1.19  2005/05/08 15:37:28  tobega
 * Fixed up style caching so it really works (internalize CascadedStyles and let each CalculatedStyle keep track of its derived children)
 *
 * Revision 1.18  2005/05/08 14:51:21  tobega
 * Removed the need for the Styler
 *
 * Revision 1.17  2005/05/08 14:36:54  tobega
 * Refactored away the need for having a context in a CalculatedStyle
 *
 * Revision 1.16  2005/04/07 16:33:34  pdoubleya
 * Fix border width if set to "none" in CSS (Kevin).
 *
 * Revision 1.15  2005/03/24 23:16:33  pdoubleya
 * Added use of SharedContext (Kevin).
 *
 * Revision 1.14  2005/02/03 23:15:50  pdoubleya
 * .
 *
 * Revision 1.13  2005/01/29 20:22:20  pdoubleya
 * Clean/reformat code. Removed commented blocks, checked copyright.
 *
 * Revision 1.12  2005/01/25 12:46:12  pdoubleya
 * Refactored duplicate code into separate method.
 *
 * Revision 1.11  2005/01/24 22:46:43  pdoubleya
 * Added support for ident-checks using IdentValue instead of string comparisons.
 *
 * Revision 1.10  2005/01/24 19:01:05  pdoubleya
 * Mass checkin. Changed to use references to CSSName, which now has a Singleton instance for each property, everywhere property names were being used before. Removed commented code. Cascaded and Calculated style now store properties in arrays rather than maps, for optimization.
 *
 * Revision 1.9  2005/01/24 14:36:31  pdoubleya
 * Mass commit, includes: updated for changes to property declaration instantiation, and new use of DerivedValue. Removed any references to older XR... classes (e.g. XRProperty). Cleaned imports.
 *
 * Revision 1.8  2004/12/05 18:11:36  tobega
 * Now uses style cache for pseudo-element styles. Also started preparing to replace inline node handling with inline content handling.
 *
 * Revision 1.7  2004/12/05 00:48:54  tobega
 * Cleaned up so that now all property-lookups use the CalculatedStyle. Also added support for relative values of top, left, width, etc.
 *
 * Revision 1.6  2004/11/15 12:42:23  pdoubleya
 * Across this checkin (all may not apply to this particular file)
 * Changed default/package-access members to private.
 * Changed to use XRRuntimeException where appropriate.
 * Began move from System.err.println to std logging.
 * Standard code reformat.
 * Removed some unnecessary SAC member variables that were only used in initialization.
 * CVS log section.
 *
 *
 */


---
name: Warm Toon Modern
colors:
  surface: '#fbf8ff'
  surface-dim: '#dbd9e1'
  surface-bright: '#fbf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f5f2fb'
  surface-container: '#efecf5'
  surface-container-high: '#eae7ef'
  surface-container-highest: '#e4e1ea'
  on-surface: '#1b1b21'
  on-surface-variant: '#4d4632'
  inverse-surface: '#303036'
  inverse-on-surface: '#f2eff8'
  outline: '#7f7660'
  outline-variant: '#d1c6ab'
  surface-tint: '#735c00'
  primary: '#735c00'
  on-primary: '#ffffff'
  primary-container: '#facc15'
  on-primary-container: '#6c5700'
  inverse-primary: '#eec200'
  secondary: '#00668a'
  on-secondary: '#ffffff'
  secondary-container: '#40c2fd'
  on-secondary-container: '#004d6a'
  tertiary: '#a43073'
  on-tertiary: '#ffffff'
  tertiary-container: '#ffbfda'
  on-tertiary-container: '#9d2b6d'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffe083'
  primary-fixed-dim: '#eec200'
  on-primary-fixed: '#231b00'
  on-primary-fixed-variant: '#574500'
  secondary-fixed: '#c4e7ff'
  secondary-fixed-dim: '#7bd0ff'
  on-secondary-fixed: '#001e2c'
  on-secondary-fixed-variant: '#004c69'
  tertiary-fixed: '#ffd8e7'
  tertiary-fixed-dim: '#ffafd3'
  on-tertiary-fixed: '#3d0026'
  on-tertiary-fixed-variant: '#85145a'
  background: '#fbf8ff'
  on-background: '#1b1b21'
  surface-variant: '#e4e1ea'
typography:
  display-hero:
    fontFamily: Plus Jakarta Sans
    fontSize: 56px
    fontWeight: '800'
    lineHeight: 64px
    letterSpacing: -0.03em
  display-hero-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 36px
    fontWeight: '800'
    lineHeight: 44px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 36px
    fontWeight: '800'
    lineHeight: 44px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '800'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '700'
    lineHeight: 28px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 28px
    letterSpacing: 0em
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 24px
    letterSpacing: 0em
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '700'
    lineHeight: 18px
    letterSpacing: 0.02em
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.03em
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 10px
    fontWeight: '800'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1.5rem
  gutter-mobile: 1rem
  margin: 2.5rem
  margin-mobile: 1.25rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style
This design system marries the clarity and restraint of modern Scandinavian minimalism with the tactile, high-energy charm of mid-century flat animation and illustrated editorial graphics. The visual voice is witty, confident, and approachable—delivering functional efficiency without the sterile coldness of standard software suites.

The style relies on crisp structural line-work (1.5px to 2px solid charcoal borders), hyper-tactile offsets, and substantial whitespace to let dense information breathe. Key traits include:
- **Playful Rigor:** Bold outlines define precise boundaries; cards and buttons feel like physical, die-cut paper stock.
- **Warm Tactility:** Surfaces evoke premium uncoated vellum rather than glossy glass screens.
- **Uncluttered Hierarchy:** Bold iconography and expressive, rounded typography direct user focus without visual noise.

## Colors
The palette is grounded in warm, uncoated paper tones accented with vibrant, high-saturation ink fills and crisp line art:

- **Primary (`#FACC15` - Banana Yellow):** The signature electric accent. Reserved for hero actions, high-priority state badges, active indicators, and attention points.
- **Secondary (`#38BDF8` - Sky Cyan):** Provides a crisp, balancing contrast for informational callouts, secondary tools, and supportive highlights.
- **Tertiary (`#F472B6` - Bubblegum Pink):** An expressive punch for playful tags, notifications, and micro-delights.
- **Neutral (`#1E1E24` - Carbon Ink):** A deep, warm charcoal used for all borders, high-contrast typography, and physical drop shadows. Never use pure black (`#000000`).
- **Canvas (`#FFFDF7` - Warm Vellum):** The primary background color. Soft on the eyes, distinctly paper-like, preventing harsh starkness.
- **Surface Elevation (`#FFFFFF` - Pure Cardstock):** Interior card panels and active control surfaces to create crisp definition against the vellum canvas.

## Typography
Plus Jakarta Sans delivers structural geometry with warm, rounded terminals, echoing a refined comic book aesthetic while maintaining top-tier legibility.

- **Weight Pairing:** Reserve `800` (Extra Bold) for primary titles, display headings, and numeric counters. Use `700` (Bold) for interactive labels and subheads. Use `500` (Medium) for body copy; avoid thin weights (`<500`) to sustain ink-stroke authority.
- **Tight Kerning:** Large headlines feature slightly negative tracking (`-0.02em` to `-0.03em`) to mimic punchy cartoon title plates.
- **Compact Vertical Cadence:** Maintain disciplined line-heights to keep cartoon-style text groups visually cohesive.

## Layout & Spacing
The layout adheres to a flexible 12-column grid system (4 columns on mobile, 8 on tablet, 12 on desktop) centered within an intentional maximum container width of 1240px.

- **Rhythm & Margin:** Substantial outer boundaries (`margin`) maintain the minimalist comic-strip framing. Internal card padding must balance thick outlines with generous breathing room (`space-lg` to `space-xl`).
- **Dense Grouping, Wide Separation:** Keep related form controls or card headers grouped tightly (`space-sm` to `space-md`), while maintaining noticeable distances between distinct conceptual blocks (`space-xl`).
- **Breakpoints:**
  - **Mobile (<768px):** Single-column stacks, `1.25rem` outer margins, elements collapse to full width with sticky touch targets.
  - **Tablet (768px - 1024px):** 2-column card structures, `1.5rem` gutters.
  - **Desktop (>1024px):** Full 12-column fluid grid with fixed column gutters of `1.5rem`.

## Elevation & Depth
This design system avoids soft, atmospheric, or blurred drop shadows. Depth is achieved entirely through physical **hard offset shadows** and crisp line weight:

- **Outline Foundation:** All interactive containers, dialogs, cards, and buttons feature a uniform `2px` solid `#1E1E24` stroke.
- **The Flat Offset Shadow:**
  - **Level 1 (Cards, List rows):** `box-shadow: 3px 3px 0px 0px #1E1E24;`
  - **Level 2 (Buttons, Badges, Hover states):** `box-shadow: 4px 4px 0px 0px #1E1E24;`
  - **Level 3 (Modals, Popovers, Floating action items):** `box-shadow: 6px 6px 0px 0px #1E1E24;`
- **Active Tactility:** On `:hover`, elements shift slightly up and left (`transform: translate(-1px, -1px)` with matching shadow expansion). On `:active` (click/press), elements snap down and right (`transform: translate(2px, 2px); box-shadow: 0px 0px 0px 0px #1E1E24;`), providing immediate physical feedback.

## Shapes
Forms are chunky, organic, and friendly, contrasting the sharp ink outlines with hyper-curved corners.

- **Base Factor:** Roundedness is globally pegged to tier `3` (Pill-shaped).
- **Small Controls (Buttons, Inputs, Badges, Chips):** Fully pill-shaped (`border-radius: 9999px`).
- **Surface Cards & Modals:** Standardized on `rounded-2xl` (`1.5rem` / `24px`) to `rounded-3xl` (`2rem` / `32px`).
- **Corners and Joints:** Never mix sharp 90-degree corners with rounded cards. When nested, inner components scale down their corner radius proportionately to ensure concentric curvature.

## Components

### Buttons
- **Primary:** Full pill shape, `#FACC15` fill, `2px` solid `#1E1E24` border, `box-shadow: 3px 3px 0px #1E1E24`, bold text. On press, zero-out shadow and translate down-right.
- **Secondary:** White canvas fill, `2px` solid `#1E1E24` stroke, matching hard shadow. On hover, background shifts to `#38BDF8` or warm parchment.
- **Sizes:** Large (`52px` height, `24px` horizontal pad), Medium (`42px` height, `18px` horizontal pad).

### Cards & Panels
- **Structure:** Crisp white surface (`#FFFFFF`), `2px` solid `#1E1E24` border, `rounded-2xl` (`24px`), `box-shadow: 4px 4px 0px #1E1E24`.
- **Inner Padding:** `space-lg` (`24px`).
- **Header Accents:** Optional bottom border (`2px solid #1E1E24`) cleanly demarcating title areas from card bodies.

### Input Fields
- **Container:** Pill-shaped (`border-radius: 9999px`) or `rounded-xl` (`16px`), height `48px`, `2px` solid `#1E1E24`.
- **State Feedback:** Focused inputs produce a vibrant `#38BDF8` or `#FACC15` hard outline ring (`box-shadow: 3px 3px 0px #1E1E24`) without blurry outer glows.
- **Placeholder:** Muted charcoal tint (`#1E1E24` at 45% opacity).

### Chips & Badges
- **Form:** Pill shape (`rounded-full`), height `24px` to `28px`, `1.5px` solid `#1E1E24` perimeter, uppercase `label-sm` font.
- **Variants:** Distinct flat fills using palette tones (Banana Yellow for "Pending", Bubblegum Pink for "Urgent", Sky Cyan for "Active").

### Checkboxes & Radio Controls
- **Checkboxes:** `20px x 20px` squares with `rounded-md` (`6px`), `2px` solid `#1E1E24`. Checked state fills `#FACC15` with a thick `#1E1E24` cartoon check mark.
- **Radios:** `20px x 20px` circles with `2px` solid `#1E1E24`. Selected state displays a centered solid `#1E1E24` circle dot (`8px`).

### Popovers & Tooltips
- **Carton Dialogue Bubble:** Tooltips feature a tiny triangle indicator referencing classic comic balloons, finished with a `2px` outline and pure `#FFFFFF` or `#FACC15` body.
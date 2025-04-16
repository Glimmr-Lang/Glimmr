package org.glmr;
/** Localizable strings for {@link org.glmr}. */
class Bundle {
    /**
     * @return <i>Source</i>
     * @see GlimmrDataObject
     */
    static String LBL_Glimmr_EDITOR() {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "LBL_Glimmr_EDITOR");
    }
    /**
     * @return <i>Files of Glimmr</i>
     * @see GlimmrDataObject
     */
    static String LBL_Glimmr_LOADER() {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "LBL_Glimmr_LOADER");
    }
    /**
     * @return <i>Visual</i>
     * @see GlimmrVisualElement
     */
    static String LBL_Glimmr_VISUAL() {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "LBL_Glimmr_VISUAL");
    }
    private Bundle() {}
}

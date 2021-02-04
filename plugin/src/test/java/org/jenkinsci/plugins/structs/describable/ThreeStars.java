package org.jenkinsci.plugins.structs.describable;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * @author Kohsuke Kawaguchi
 * @see <a href="https://en.wikipedia.org/wiki/Flag_of_Tennessee">Tennessee state flag</a>
 */
public class ThreeStars extends AbstractDescribableImpl<ThreeStars> {

    private final String one;
    private final String two;
    private final String three;

    @DataBoundConstructor
    public ThreeStars(String one, String two, String three) {
        this.one = one;
        this.two = two;
        this.three = three;
    }


    @Extension
    @Symbol("tennessee")
    public static class DescriptorImpl extends Descriptor<ThreeStars> {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Tri-star state";
        }
    }
}

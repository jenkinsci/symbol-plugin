package org.jenkinsci.plugins.structs.describable;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * To test a single argument Describable
 *
 * @author Kohsuke Kawaguchi
 */
public class LoneStar extends AbstractDescribableImpl<LoneStar> {
    public String star;
    public String capital;

    @DataBoundConstructor
    public LoneStar(String star) {
        this.star = star;
    }

    // can have optional parameters
    @DataBoundSetter
    public void setCapital(String capital) {
        this.capital = capital;
    }

    @Extension
    @Symbol("texas")
    public static class DescriptorImpl extends Descriptor<LoneStar> {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Lone star state";
        }
    }
}

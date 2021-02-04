package org.jenkinsci.plugins.structs;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * "net" symbols are on two descriptors.
 *
 * @author Kohsuke Kawaguchi
 */
public class FishingNet extends AbstractDescribableImpl<FishingNet> implements Fishing {
    @DataBoundConstructor
    public FishingNet() {
    }


    @Extension @Symbol("net")
    public static class DescriptorImpl extends Descriptor<FishingNet> {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "fishing net";
        }
    }
}

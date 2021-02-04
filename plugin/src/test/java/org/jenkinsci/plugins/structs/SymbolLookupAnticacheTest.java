package org.jenkinsci.plugins.structs;

import hudson.model.Descriptor;
import jenkins.tasks.SimpleBuildStep;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;

/**
 * @author Sam Van Oort
 */
public class SymbolLookupAnticacheTest {
    @Rule
    public JenkinsRule rule = new JenkinsRule();

    /** Verify that if we install a new plugin with a Symbol use, that symbol is found.
     *  Without the plugin we hit the "anticache" i.e. {@link SymbolLookup#noHitCache} that the symbol does not exist.
     *  Once we add the plugin it should hit the cache.
     */
    @Test
    public void testAnticache() throws Exception{
        Descriptor d = SymbolLookup.get().findDescriptor(SimpleBuildStep.class, "trivialBuilder");
        Descriptor stepDescriptor = SymbolLookup.get().find(Descriptor.class, "trivialBuilder");
        assert d == null;
        assert stepDescriptor == null;

        // See the test-plugin folder to rebuild this
        File plugin = new File(SymbolLookupAnticacheTest.class.getResource("/structs-test-plugin.hpi").toURI());
        rule.jenkins.getPluginManager().dynamicLoad(plugin);

        d = SymbolLookup.get().findDescriptor(SimpleBuildStep.class, "trivialBuilder");
        stepDescriptor = SymbolLookup.get().find(Descriptor.class, "trivialBuilder");
        assert d != null;
        assert stepDescriptor != null;
        Assert.assertEquals("simpleBuild", d.getDisplayName());
        Assert.assertEquals("org.jenkinsci.plugins.structstest.TrivialBuilder", stepDescriptor.clazz.getName());

        // Once more just to confirm there's no wackiness when hitting the normal cache too
        Assert.assertEquals(SymbolLookup.get().findDescriptor(SimpleBuildStep.class, "trivialBuilder"), d);
    }


}

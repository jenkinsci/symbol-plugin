/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.structs.describable;

import com.google.common.collect.ImmutableMap;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BooleanParameterValue;
import hudson.model.Descriptor;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserMergeOptions;
import hudson.plugins.git.extensions.impl.CleanBeforeCheckout;
import java.util.HashMap;
import org.codehaus.groovy.runtime.GStringImpl;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.structs.Fishing;
import org.jenkinsci.plugins.structs.FishingNet;
import org.jenkinsci.plugins.structs.Internet;
import org.jenkinsci.plugins.structs.Tech;
import org.jenkinsci.plugins.structs.describable.first.SharedName;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import static org.apache.commons.lang3.SerializationUtils.roundtrip;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.jenkinsci.plugins.structs.describable.DescribableModel.*;
import static org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable.ANONYMOUS_KEY;
import static org.junit.Assert.*;

@SuppressWarnings("unchecked") // generic array construction
public class DescribableModelTest {
    @ClassRule
    public static JenkinsRule rule = new JenkinsRule();
    @ClassRule
    public static LoggerRule logging = new LoggerRule().record(DescribableModel.class, Level.ALL);

    @Test
    public void instantiate() throws Exception {
        Map<String,Object> args = map("text", "hello", "flag", true);
        assertEquals("C:hello/true", instantiate(C.class, args).toString());
        args.put("value", "main");
        assertEquals("I:main/hello/true", instantiate(I.class, args).toString());
        assertEquals("C:goodbye/false", instantiate(C.class, map("text", "goodbye")).toString());
    }

    @Test
    public void erroneousParameters() {
        try {
            Map<String,Object> args = map("text", "hello", "flag", true, "garbage", "!", "junk", "splat");
            instantiate(C.class,  args);
            Assert.fail("Instantiation should have failed due to unnecessary arguments");
        } catch (Exception e) {
            assertThat(e, Matchers.instanceOf(IllegalArgumentException.class));
            assertThat(e.getMessage(), is("WARNING: Unknown parameter(s) found for class type '" +
                C.class.getName() + "': garbage,junk"));
        }
    }

    private <T> T instantiate(Class<T> type, Map<String, Object> args) throws Exception {
        return new DescribableModel<>(type).instantiate(args);
    }

    @Test public void uninstantiate() throws Exception {
        assertEquals("{flag=true, text=stuff}", DescribableModel.uninstantiate_(new C("stuff", true)).toString());
        I i = new I("stuff");
        i.setFlag(true);
        i.text = "more";
        assertEquals("{flag=true, text=more, value=stuff}", DescribableModel.uninstantiate_(i).toString());

        Object net = new Internet();
        UninstantiatedDescribable ud = UninstantiatedDescribable.from(net);
        assertEquals("net",ud.getSymbol());
        assertTrue(ud.getArguments().isEmpty());
        assertTrue(ud.instantiate(Tech.class) instanceof Internet);
    }

    @Test public void mismatchedTypes() throws Exception {
        try {
            instantiate(I.class, map("value", 99));
            fail();
        } catch (Exception x) {
            String message = x.getMessage();
            assertTrue(message, message.contains(I.class.getName()));
            assertTrue(message, message.contains("value"));
            assertTrue(message, message.contains("java.lang.String"));
            assertTrue(message, message.contains("java.lang.Integer"));
        }
    }

    @Test public void schemaFor() throws Exception {
        schema(C.class, "C(text: String, flag: boolean, shorty?: short, toBeRemoved?(deprecated): String)");
        schema(I.class, "I(value: String, flag?: boolean, text?: String)");
        DescribableModel<?> schema = new DescribableModel(Impl1.class);
        assertEquals("Implementation #1", schema.getDisplayName());
        assertEquals("<div>Overall help.</div>", schema.getHelp());
        assertEquals("<div>The text to display.</div>", schema.getParameter("text").getHelp());
        schema = new DescribableModel<>(C.class);
        assertEquals("C", schema.getDisplayName());
        assertNull(schema.getHelp());
        assertNull(schema.getParameter("text").getHelp());
        assertFalse(schema.getParameter("text").isDeprecated());
        assertTrue(schema.getParameter("toBeRemoved").isDeprecated());
    }

    public static final class C {
        public final String text;
        private final boolean flag;
        private String toBeRemoved;
        @DataBoundConstructor
        public C(String text, boolean flag) {
            this.text = text;
            this.flag = flag;
        }
        public boolean isFlag() {
            return flag;
        }
        @Override public String toString() {
            return "C:" + text + "/" + flag;
        }
        // Are not actually trying to inject it; just making sure that unhandled @DataBoundSetter types are ignored if unused.
        public short getShorty() {return 0;}
        @DataBoundSetter
        public void setShorty(short s) {throw new UnsupportedOperationException();}
        public String getToBeRemoved() {
            return toBeRemoved;
        }
        @Deprecated
        @DataBoundSetter
        public void setToBeRemoved(String toBeRemoved) {
            this.toBeRemoved = toBeRemoved;
        }
    }

    public static final class I {
        private final String value;
        @DataBoundSetter private String text;
        private boolean flag;
        @DataBoundConstructor public I(String value) {
            this.value = value;
        }
        public String getValue() {
            return value;
        }
        public String getText() {
            return text;
        }
        public boolean isFlag() {
            return flag;
        }
        @DataBoundSetter public void setFlag(boolean f) {
            this.flag = f;
        }
        @Override public String toString() {
            return "I:" + value + "/" + text + "/" + flag;
        }
    }

    @Test public void findSubtypes() throws Exception {
        assertEquals(new HashSet<Class<?>>(Arrays.asList(Impl1.class, Impl2.class, Impl3.class, Impl4.class)), DescribableModel.findSubtypes(Base.class));
        assertEquals(Collections.singleton(Impl1.class), DescribableModel.findSubtypes(Marker.class));
    }

    @Test public void bindMapsFQN() throws Exception {
        assertEquals("UsesBase[Impl1[hello]]", instantiate(UsesBase.class, map("base", map(CLAZZ, Impl1.class.getName(), "text", "hello"))).toString());
    }

    // TODO also check case that a FQN is needed

    @Test public void gstring() throws Exception {
        assertEquals("UsesBase[Impl1[hello world]]", instantiate(UsesBase.class, map("base", map(CLAZZ, "Impl1", "text", new GStringImpl(new Object[]{"hello", "world"}, new String[]{"", " "})))).toString());
    }

    @Test public void nestedStructs() throws Exception {
        roundTrip(UsesBase.class, map("base", map(CLAZZ, "Impl1", "text", "hello")));
        roundTrip(UsesBase.class, map("base", map(CLAZZ, "Impl2", "flag", true)));
        roundTrip(UsesImpl2.class, map("impl2", map()));
        schema(UsesBase.class, "UsesBase(base: Base{Impl1(text: String) | Impl2(flag?: boolean) | Impl3(base: Base…) | Impl4(bases: Base…[])})");
        schema(UsesImpl2.class, "UsesImpl2(impl2: Impl2(flag?: boolean))");
        schema(UsesUnimplementedExtensionPoint.class, "UsesUnimplementedExtensionPoint(delegate: UnimplementedExtensionPoint{})");
        schema(UsesSomeImplsBroken.class, "UsesSomeImplsBroken(delegate: SomeImplsBroken{FineImpl()})");
    }

    public static class UsesBase {
        public final Base base;
        @DataBoundConstructor public UsesBase(Base base) {
            this.base = base;
        }
        @Override public String toString() {
            return "UsesBase[" + base + "]";
        }
    }

    public static class UsesImpl2 {
        public final Impl2 impl2;
        @DataBoundConstructor public UsesImpl2(Impl2 impl2) {
            this.impl2 = impl2;
        }
        @Override public String toString() {
            return "UsesImpl2[" + impl2 + "]";
        }
    }

    public static abstract class Base extends AbstractDescribableImpl<Base> {}

    public interface Marker {}

    public static final class Impl1 extends Base implements Marker {
        private final String text;
        @DataBoundConstructor public Impl1(String text) {
            this.text = text;
        }
        public String getText() {
            return text;
        }
        @Override public String toString() {
            return "Impl1[" + text + "]";
        }
        @Extension
        public static final class DescriptorImpl extends Descriptor<Base> {
            @Override public String getDisplayName() {
                return "Implementation #1";
            }
        }
    }

    public static final class Impl2 extends Base {
        private boolean flag;
        @DataBoundConstructor public Impl2() {}
        public boolean isFlag() {
            return flag;
        }
        @DataBoundSetter public void setFlag(boolean flag) {
            this.flag = flag;
        }
        @Override public String toString() {
            return "Impl2[" + flag + "]";
        }
        @Extension public static final class DescriptorImpl extends Descriptor<Base> {
            @Override public String getDisplayName() {
                return "Impl2";
            }
        }
    }

    //use to trigger recursion subcases
    public static final class Impl3 extends Base {
        private final Base base;
        @DataBoundConstructor public Impl3(Base base) {
            this.base = base;
        }
        public Base getBase() {
            return base;
        }
        @Override public String toString() {
            return "Impl3[" + base.toString() + "]";
        }
        @Extension public static final class DescriptorImpl extends Descriptor<Base> {
            @Override public String getDisplayName() {
                return "Impl3";
            }
        }
    }
    public static final class Impl4 extends Base {
        private final Base[] bases;
        @DataBoundConstructor public Impl4(Base[] bases) {
            this.bases = bases;
        }
        public Base[] getBases() {
            return bases;
        }
        @Override public String toString() {
            return "Impl4[" + Arrays.toString(bases) + "]";
        }
        @Extension public static final class DescriptorImpl extends Descriptor<Base> {
            @Override public String getDisplayName() {
                return "Impl4";
            }
        }
    }

    public static abstract class UnimplementedExtensionPoint extends AbstractDescribableImpl<UnimplementedExtensionPoint> {}
    public static final class UsesUnimplementedExtensionPoint {
        @DataBoundConstructor public UsesUnimplementedExtensionPoint(UnimplementedExtensionPoint delegate) {}
    }

    public static abstract class SomeImplsBroken extends AbstractDescribableImpl<SomeImplsBroken> {}
    public static class BrokenImpl extends SomeImplsBroken {
        @Extension public static class DescriptorImpl extends Descriptor<SomeImplsBroken> {
            @Override @Nonnull public String getDisplayName() {
                return "BrokenImpl";
            }
        }
    }
    public static class FineImpl extends SomeImplsBroken {
        @DataBoundConstructor public FineImpl() {}
        @Extension public static class DescriptorImpl extends Descriptor<SomeImplsBroken> {
            @Override @Nonnull public String getDisplayName() {
                return "FineImpl";
            }
        }
    }
    public static class UsesSomeImplsBroken {
        @DataBoundConstructor public UsesSomeImplsBroken(SomeImplsBroken delegate) {}
    }

    @Test public void enums() throws Exception {
        roundTrip(UsesEnum.class, map("e", "ZERO"));
        schema(UsesEnum.class, "UsesEnum(e: E[ZERO])");
    }

    @Test public void enumsWithGString() throws Exception {
        assertEquals("UsesEnum[ZERO]", instantiate(UsesEnum.class, map("e", new GStringImpl(new Object[0], new String[]{"ZERO"}))).toString());
    }

    public static final class UsesEnum {
        private final E e;
        @DataBoundConstructor public UsesEnum(E e) {
            this.e = e;
        }
        public E getE() {
            return e;
        }
        @Override public String toString() {
            return "UsesEnum[" + e + "]";
        }
    }
    public enum E {
        ZERO() {@Override public int v() {return 0;}};
        public abstract int v();
    }

    @Test public void urls() throws Exception {
        roundTrip(UsesURL.class, map("u", "http://nowhere.net/"));
        schema(UsesURL.class, "UsesURL(u?: String)");
    }

    @Test public void urlsWithGString() throws Exception {
        assertEquals("UsesURL[http://nowhere.net/]", instantiate(UsesURL.class, map("u", new GStringImpl(new Object[0], new String[]{"http://nowhere.net/"}))).toString());
    }

    public static final class UsesURL {
        @DataBoundConstructor public UsesURL() {}
        @DataBoundSetter public URL u;
        @Override public String toString() {
            return "UsesURL[" + u + "]";
        }
    }

    @Test public void result() throws Exception {
        roundTrip(UsesResult.class, map("r", "SUCCESS"));
        schema(UsesResult.class, "UsesResult(r?: String)");
    }

    public static final class UsesResult {
        @DataBoundConstructor public UsesResult() {}
        @DataBoundSetter public Result r;
        @Override public String toString() {
            return "UsesResult[" + r + "]";
        }
    }

    @Test public void chars() throws Exception {
        roundTrip(UsesCharacter.class, map("c", "!"));
        schema(UsesCharacter.class, "UsesCharacter(c?: char)");
    }

    @Test public void charsWithGString() throws Exception {
        assertEquals("UsesCharacter[x]", instantiate(UsesCharacter.class, map("c", new GStringImpl(new Object[0], new String[]{"x"}))).toString());
    }

    public static final class UsesCharacter {
        @DataBoundConstructor public UsesCharacter() {}
        @DataBoundSetter public char c;
        @Override public String toString() {
            return "UsesCharacter[" + c + "]";
        }
    }

    @Test public void stringArray() throws Exception {
        roundTrip(UsesStringArray.class, map("strings", Arrays.asList("one", "two")));
        schema(UsesStringArray.class, "UsesStringArray(strings: String[])");
    }

    @Test public void stringList() throws Exception {
        roundTrip(UsesStringList.class, map("strings", Arrays.asList("one", "two")));
        schema(UsesStringList.class, "UsesStringList(strings: String[])");
    }

    public static final class UsesStringArray {
        private final String[] strings;
        @DataBoundConstructor public UsesStringArray(String[] strings) {
            this.strings = strings;
        }
        public String[] getStrings() {
            return strings;
        }
    }

    public static final class UsesStringList {
        private final List<String> strings;
        @DataBoundConstructor public UsesStringList(List<String> strings) {
            this.strings = strings;
        }
        public List<String> getStrings() {
            return strings;
        }
    }

    @Test public void structArrayHomo() throws Exception {
        roundTrip(UsesStructArrayHomo.class, map("impls", Arrays.asList(map(), map("flag", true))), "UsesStructArrayHomo[Impl2[false], Impl2[true]]");
        schema(UsesStructArrayHomo.class, "UsesStructArrayHomo(impls: Impl2(flag?: boolean)[])");
    }

    public static final class UsesStructArrayHomo {
        private final Impl2[] impls;
        @DataBoundConstructor public UsesStructArrayHomo(Impl2[] impls) {
            this.impls = impls;
        }
        public Impl2[] getImpls() {
            return impls;
        }
        @Override public String toString() {
            return "UsesStructArrayHomo" + Arrays.toString(impls);
        }
    }

    @Test public void structListHomo() throws Exception {
        roundTrip(UsesStructListHomo.class, map("impls", Arrays.asList(map(), map("flag", true))), "UsesStructListHomo[Impl2[false], Impl2[true]]");
        schema(UsesStructListHomo.class, "UsesStructListHomo(impls: Impl2(flag?: boolean)[])");
    }

    public static final class UsesStructListHomo {
        private final List<Impl2> impls;
        @DataBoundConstructor public UsesStructListHomo(List<Impl2> impls) {
            this.impls = impls;
        }
        public List<Impl2> getImpls() {
            return impls;
        }
        @Override public String toString() {
            return "UsesStructListHomo" + impls;
        }
    }

    @Test public void structCollectionHomo() throws Exception {
        roundTrip(UsesStructCollectionHomo.class, map("impls", Arrays.asList(map(), map("flag", true))), "UsesStructCollectionHomo[Impl2[false], Impl2[true]]");
        schema(UsesStructCollectionHomo.class, "UsesStructCollectionHomo(impls: Impl2(flag?: boolean)[])");
    }

    public static final class UsesStructCollectionHomo {
        private final Collection<Impl2> impls;
        @DataBoundConstructor public UsesStructCollectionHomo(Collection<Impl2> impls) {
            this.impls = impls;
        }
        public Collection<Impl2> getImpls() {
            return impls;
        }
        @Override public String toString() {
            return "UsesStructCollectionHomo" + impls;
        }
    }

    @Test public void structArrayHetero() throws Exception {
        roundTrip(UsesStructArrayHetero.class, map("bases", Arrays.asList(map(CLAZZ, "Impl1", "text", "hello"), map(CLAZZ, "Impl2", "flag", true))), "UsesStructArrayHetero[Impl1[hello], Impl2[true]]");
        schema(UsesStructArrayHetero.class, "UsesStructArrayHetero(bases: Base{Impl1(text: String) | Impl2(flag?: boolean) | Impl3(base: Base…) | Impl4(bases: Base…[])}[])");
    }

    public static final class UsesStructArrayHetero {
        private final Base[] bases;
        @DataBoundConstructor public UsesStructArrayHetero(Base[] bases) {
            this.bases = bases;
        }
        public Base[] getBases() {
            return bases;
        }
        @Override public String toString() {
            return "UsesStructArrayHetero" + Arrays.toString(bases);
        }
    }

    @Test public void structListHetero() throws Exception {
        roundTrip(UsesStructListHetero.class, map("bases", Arrays.asList(map(CLAZZ, "Impl1", "text", "hello"), map(CLAZZ, "Impl2", "flag", true))), "UsesStructListHetero[Impl1[hello], Impl2[true]]");
        schema(UsesStructListHetero.class, "UsesStructListHetero(bases: Base{Impl1(text: String) | Impl2(flag?: boolean) | Impl3(base: Base…) | Impl4(bases: Base…[])}[])");
    }

    public static final class UsesStructListHetero {
        private final List<Base> bases;
        @DataBoundConstructor public UsesStructListHetero(List<Base> bases) {
            this.bases = bases;
        }
        public List<Base> getBases() {
            return bases;
        }
        @Override public String toString() {
            return "UsesStructListHetero" + bases;
        }
    }

    @Test public void structCollectionHetero() throws Exception {
        roundTrip(UsesStructCollectionHetero.class, map("bases", Arrays.asList(map(CLAZZ, "Impl1", "text", "hello"), map(CLAZZ, "Impl2", "flag", true))), "UsesStructCollectionHetero[Impl1[hello], Impl2[true]]");
        schema(UsesStructCollectionHetero.class, "UsesStructCollectionHetero(bases: Base{Impl1(text: String) | Impl2(flag?: boolean) | Impl3(base: Base…) | Impl4(bases: Base…[])}[])");
    }

    public static final class UsesStructCollectionHetero {
        private final Collection<Base> bases;
        @DataBoundConstructor public UsesStructCollectionHetero(Collection<Base> bases) {
            this.bases = bases;
        }
        public Collection<Base> getBases() {
            return bases;
        }
        @Override public String toString() {
            return "UsesStructCollectionHetero" + bases;
        }
    }

    @Test public void defaultValuesStructCollectionCommon() throws Exception {
        roundTrip(DefaultStructCollection.class, map("bases", Collections.singletonList(map(CLAZZ, "Impl1", "text", "special"))), "DefaultStructCollection[Impl1[special]]");
    }

    @Test public void defaultValuesStructCollectionEmpty() throws Exception {
        roundTrip(DefaultStructCollection.class, map("bases", Collections.emptyList()), "DefaultStructCollection[]");
    }

    @Issue("JENKINS-25779")
    @Test public void defaultValuesStructCollection() throws Exception {
        roundTrip(DefaultStructCollection.class, map(), "DefaultStructCollection[Impl1[default]]");
    }

    @Issue("JENKINS-25779")
    @Test public void defaultValuesNestedStruct() throws Exception {
        roundTrip(DefaultStructCollection.class, map("bases", Arrays.asList(map(CLAZZ, "Impl2"), map(CLAZZ, "Impl2", "flag", true))), "DefaultStructCollection[Impl2[false], Impl2[true]]");
    }

    @Issue("JENKINS-25779")
    @Test public void defaultValuesNullSetter() throws Exception {
        roundTrip(DefaultStructCollection.class, map("bases", null), "DefaultStructCollectionnull");
    }

    public static final class DefaultStructCollection {
        private Collection<Base> bases = Collections.singletonList(new Impl1("default"));
        @DataBoundConstructor public DefaultStructCollection() {}
        public Collection<Base> getBases() {return bases;}
        @DataBoundSetter public void setBases(Collection<Base> bases) {this.bases = bases;}
        @Override public String toString() {return "DefaultStructCollection" + bases;}
    }

    @Test public void defaultValuesStructArrayCommon() throws Exception {
        roundTrip(DefaultStructArray.class, map("bases", Collections.singletonList(map(CLAZZ, "Impl1", "text", "special")), "stuff", "val"), "DefaultStructArray[Impl1[special]];stuff=val");
    }

    @Issue("JENKINS-25779")
    @Test public void defaultValuesStructArray() throws Exception {
        roundTrip(DefaultStructArray.class, map("stuff", "val"), "DefaultStructArray[Impl1[default], Impl2[true]];stuff=val");
    }

    @Issue("JENKINS-25779")
    @Test public void defaultValuesNullConstructorParameter() throws Exception {
        roundTrip(DefaultStructArray.class, map(), "DefaultStructArray[Impl1[default], Impl2[true]];stuff=null");
    }

    public static final class DefaultStructArray {
        private final String stuff;
        private Base[] bases;
        @DataBoundConstructor public DefaultStructArray(String stuff) {
            this.stuff = stuff;
            Impl2 impl2 = new Impl2();
            impl2.setFlag(true);
            bases = new Base[] {new Impl1("default"), impl2};
        }
        public Base[] getBases() {return bases;}
        @DataBoundSetter public void setBases(Base[] bases) {this.bases = bases;}
        public String getStuff() {return stuff;}
        @Override public String toString() {return "DefaultStructArray" + Arrays.toString(bases) + ";stuff=" + stuff;}
    }

    @Issue("JENKINS-26093")
    @Test public void parameterValues() throws Exception {
        assertTrue(DescribableModel.findSubtypes(ParameterValue.class).contains(BooleanParameterValue.class)); //  do not want to enumerate ListSubversionTagsParameterValue etc.
        // Omitting RunParameterValue since it is not friendly for a unit test.
        // JobParameterDefinition is not registered as an extension, so not supporting FileParameterValue.
        // FileParameterValue is unsupportable as an input to a WorkflowJob since it requires createBuildWrapper to work;
        // as an argument to BuildTriggerStep it could perhaps work, but we would need to provide a custom FileItem implementation.
        // PasswordParameterValue requires Secret.fromString and thus JenkinsRule.
        // For others: https://github.com/search?type=Code&q=user%3Ajenkinsci+user%3Acloudbees+%22extends+ParameterDefinition%22
        roundTrip(TakesParams.class, map("parameters", Arrays.asList(
                map(CLAZZ, "BooleanParameterValue", "name", "flag", "value", true),
                map(CLAZZ, "StringParameterValue", "name", "n", "value", "stuff"),
                map(CLAZZ, "TextParameterValue", "name", "text", "value", "here\nthere"))),
            "TakesParams;BooleanParameterValue:flag=true;StringParameterValue:n=stuff;TextParameterValue:text=here\nthere");
        assertEquals("(parameters=[@booleanParam$BooleanParameterValue(name=flag,value=true)])", DescribableModel.uninstantiate2_(new TakesParams(Collections.singletonList(new BooleanParameterValue("flag", true)))).toString());
    }
    public static final class TakesParams {
        public final List<ParameterValue> parameters;
        @DataBoundConstructor public TakesParams(List<ParameterValue> parameters) {
            this.parameters = parameters;
        }
        @Override public String toString() {
            StringBuilder b = new StringBuilder("TakesParams");
            for (ParameterValue v : parameters) {
                b.append(';').append(v.getClass().getSimpleName()).append(':').append(v.getShortDescription());
            }
            return b.toString();
        }
    }

    @Test public void parametersDefinitionProperty() throws Exception {
        roundTrip(ParametersDefinitionProperty.class, map("parameterDefinitions", Arrays.asList(
                map(CLAZZ, "BooleanParameterDefinition", "name", "flag", "defaultValue", false),
                map(CLAZZ, "StringParameterDefinition", "name", "text", "trim", false))));
    }

    @Issue("JENKINS-26619")
    @Test public void getterDescribableList() throws Exception {
        roundTrip(GitSCM.class, map(
            "extensions", Collections.singletonList(map(CLAZZ, CleanBeforeCheckout.class.getSimpleName())),
            // Default values for these things do not work because GitSCM fails to use @DataBoundSetter:
            "branches", Collections.singletonList(map("name", "*/master")),
            "doGenerateSubmoduleConfigurations", false,
            "submoduleCfg", Collections.emptyList(),
            "userRemoteConfigs", Collections.emptyList()));
    }

    @Issue("JENKINS-34070")
    @Test public void userMergeOptions() throws Exception {
        roundTrip(UserMergeOptions.class, map("mergeRemote", "x", "mergeTarget", "y", "mergeStrategy", "OCTOPUS", "fastForwardMode", "FF_ONLY"), "UserMergeOptions{mergeRemote='x', mergeTarget='y', mergeStrategy='OCTOPUS', fastForwardMode='FF_ONLY'}");
    }

    @Issue("JENKINS-32925") // but Base3/Base4 usages are the more realistic case
    @Test
    public void recursion() throws Exception {
        schema(Recursion.class, "Recursion(foo?: Recursion…)");
    }

    public static class Recursion {
        @DataBoundConstructor
        public Recursion() {}
        @DataBoundSetter
        public void setFoo(Recursion r) {}
    }

    /**
     * Makes sure resolveClass can do both symbol & class name lookup
     */
    @Test
    public void resolveClass() throws Exception {
        assertEquals(FishingNet.class, DescribableModel.resolveClass(Fishing.class, null, "net"));
        assertEquals(FishingNet.class, DescribableModel.resolveClass(Fishing.class, "FishingNet", null));
        assertEquals(Internet.class, DescribableModel.resolveClass(Tech.class, null, "net"));
        assertEquals(Internet.class, DescribableModel.resolveClass(Tech.class, "Internet", null));
    }

    @Issue("JENKINS-46122")
    @Test
    public void resolveSymbolOnWrongBaseClass() throws Exception {
        try {
            DescribableModel.resolveClass(Tech.class, null, "rod");
            fail("No symbol for Tech should exist.");
        } catch (UnsupportedOperationException e) {
            assertEquals("no known implementation of " + Tech.class + " is using symbol ‘rod’", e.getMessage());
        }
    }

    @Test
    public void singleRequiredParameter() throws Exception {
        // positive case
        DescribableModel dc = new DescribableModel(LoneStar.class);
        assertTrue(dc.hasSingleRequiredParameter());
        assertEquals("star", dc.getSoleRequiredParameter().getName());
        assertNotNull(dc.getParameter("capital"));

        // negative case
        dc = new DescribableModel(ThreeStars.class);
        assertFalse(dc.hasSingleRequiredParameter());

        dc = new DescribableModel(LoneStar.class);
        UninstantiatedDescribable x = dc.uninstantiate2(new LoneStar("foo"));
        assertTrue(x.hasSoleRequiredArgument());

        LoneStar star = new LoneStar("foo");
        star.setCapital("Should be Dallas");
        UninstantiatedDescribable y = dc.uninstantiate2(star);
        assertFalse(y.hasSoleRequiredArgument());
    }

    @Test
    public void anonymousKey() throws Exception {
        DescribableModel dc = new DescribableModel(LoneStar.class);
        UninstantiatedDescribable u = dc.uninstantiate2(new LoneStar("texas"));
        assertFalse(u.getArguments().containsKey(ANONYMOUS_KEY)); // shouldn't show up as a key from uninstantiate
        assertEquals("texas",u.getSymbol());

        // but the key can be used during construction
        LoneStar ls = (LoneStar)new UninstantiatedDescribable(
                "texas",null,Collections.singletonMap(ANONYMOUS_KEY,"alamo")).instantiate();
        assertEquals("alamo",ls.star);

        // it cannot be used when multiple parameters are given
        try {
            new UninstantiatedDescribable(
                    "texas",null, ImmutableMap.of(ANONYMOUS_KEY,"alamo","capital","Austin")).instantiate();
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void coerceNumbersAndBoolean() throws Exception {
        IntAndBool intAndBool = (IntAndBool) new UninstantiatedDescribable("intAndBool", null,
                ImmutableMap.<String, Object>of("i", "5", "b", "true")).instantiate();
        assertEquals(5, intAndBool.i);
        assertTrue(intAndBool.b);
    }

    @Test
    public void serialization() {
        LoneStar s = new LoneStar("texas");
        DescribableModel<LoneStar> m = DescribableModel.of(LoneStar.class);
        UninstantiatedDescribable d = m.uninstantiate2(s);
        assertSame(d.getModel(),m);
        d = roundtrip(d);
        assertNotNull(d.getModel());
        assertEquals("texas",d.getSymbol());
    }

    @Test
    public void deprecated() throws Exception {
        assertTrue(new DescribableModel(ToBeRemoved.class).isDeprecated());
        assertFalse(new DescribableModel(Impl1.class).isDeprecated());
    }

    @Deprecated
    public static class ToBeRemoved {
        @DataBoundConstructor
        public ToBeRemoved() {
        }
    }

    @Issue("JENKINS-43337")
    @Test
    public void ambiguousSimpleName() throws Exception {
        AmbiguousContainer container = new AmbiguousContainer(new FirstAmbiguous.CommonName("first"),
                new UnambiguousClassName("second"));

        UninstantiatedDescribable ud = DescribableModel.uninstantiate2_(container);

        Object o = ud.toMap().get("ambiguous");
        assertTrue(o instanceof Map);
        Map<String,Object> m = (Map<String,Object>)o;

        // Make sure the ambiguous class is fully qualified.
        assertEquals(FirstAmbiguous.CommonName.class.getName(), m.get("$class"));

        Object o2 = ud.toMap().get("unambiguous");
        assertTrue(o2 instanceof Map);
        Map<String,Object> m2 = (Map<String,Object>)o2;

        // Make sure the unambiguous class just uses the simple name.
        assertEquals(UnambiguousClassName.class.getSimpleName(), m2.get("$class"));
    }

    @Issue("JENKINS-45130") //
    @Test
    public void ambiguousTopLevelSimpleName() throws Exception {
        AmbiguousContainer container = new AmbiguousContainer(new SharedName("first"),
                new UnambiguousClassName("second"));

        UninstantiatedDescribable ud = DescribableModel.uninstantiate2_(container);

        Object o = ud.toMap().get("ambiguous");
        assertTrue(o instanceof Map);
        Map<String,Object> m = (Map<String,Object>)o;

        // Make sure the ambiguous class is fully qualified.
        assertEquals(SharedName.class.getName(), m.get("$class"));

        Object o2 = ud.toMap().get("unambiguous");
        assertTrue(o2 instanceof Map);
        Map<String,Object> m2 = (Map<String,Object>)o2;

        // Make sure the unambiguous class just uses the simple name.
        assertEquals(UnambiguousClassName.class.getSimpleName(), m2.get("$class"));
    }

    @Issue("JENKINS-45130")
    @Test
    public void ambiguousTopLevelSimpleNameInList() throws Exception {
        SharedName first = new SharedName("first");
        first.setTwo("something");
        AmbiguousListContainer container = new AmbiguousListContainer(Arrays.asList(first,
                new UnambiguousClassName("second")));

        UninstantiatedDescribable ud = DescribableModel.uninstantiate2_(container);

        Object o = ud.toMap().get("list");
        assertTrue(o instanceof List);
        List<Map<String, Object>> l = (List<Map<String, Object>>) o;

        Map<String,Object> m = l.get(0);

        // Make sure the ambiguous class is fully qualified.
        assertEquals(SharedName.class.getName(), m.get("$class"));

        Map<String,Object> m2 = l.get(1);

        // Make sure the unambiguous class just uses the simple name.
        assertEquals(UnambiguousClassName.class.getSimpleName(), m2.get("$class"));

        System.out.println(ud.toString());

        AmbiguousListContainer roundtrip = (AmbiguousListContainer) ud.instantiate();
        assertThat(roundtrip.list.get(0), instanceOf(SharedName.class));
        assertThat(roundtrip.list.get(1), instanceOf(UnambiguousClassName.class));
    }

    @Issue("JENKINS-45130")
    @Test
    public void ambiguousTopLevelSimpleNameInArray() throws Exception {
        SharedName first = new SharedName("first");
        first.setTwo("something");
        AmbiguousArrayContainer container = new AmbiguousArrayContainer(first,
                new UnambiguousClassName("second"));

        UninstantiatedDescribable ud = DescribableModel.uninstantiate2_(container);

        Object o = ud.toMap().get("array");
        assertTrue(o instanceof List);
        List<Map<String, Object>> l = (List<Map<String, Object>>) o;

        Map<String,Object> m = l.get(0);

        // Make sure the ambiguous class is fully qualified.
        assertEquals(SharedName.class.getName(), m.get("$class"));

        Map<String,Object> m2 = l.get(1);

        // Make sure the unambiguous class just uses the simple name.
        assertEquals(UnambiguousClassName.class.getSimpleName(), m2.get("$class"));

        System.out.println(ud.toString());

        AmbiguousArrayContainer roundtrip = (AmbiguousArrayContainer) ud.instantiate();
        assertThat(roundtrip.getArray()[0], instanceOf(SharedName.class));
        assertThat(roundtrip.getArray()[1], instanceOf(UnambiguousClassName.class));
    }

    private static Map<String,Object> map(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException();
        }
        Map<String,Object> m = new TreeMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            m.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return m;
    }

    private void roundTrip(Class<?> c, Map<String,Object> m) throws Exception {
        roundTrip(c, m, null);
    }

    private void roundTrip(Class<?> c, Map<String,Object> m, String toString) throws Exception {
        Object o = instantiate(c, m);
        if (toString != null) {
            assertEquals(toString, o.toString());
        }
        Map<String,Object> m2 = DescribableModel.uninstantiate_(o);
        assertEquals(m, m2);
    }

    private static void schema(Class<?> c, String schema) throws Exception {
        assertEquals(schema, new DescribableModel(c).toString());
    }

    public static final class AllJavaStandardTypesClass {
        // final values (set in constructor)
        private final boolean booleanValue1;
        private final byte byteValue1;
        private final short shortValue1;
        private final int intValue1;
        private final long longValue1;
        private final float floatValue1;
        private final double doubleValue1;


        @DataBoundConstructor
        public AllJavaStandardTypesClass(boolean booleanValue1, byte byteValue1, short shortValue1,
                               int intValue1, long longValue1, float floatValue1, double doubleValue1) {
            this.booleanValue1 = booleanValue1;
            this.byteValue1 = byteValue1;
            this.shortValue1 = shortValue1;
            this.intValue1 = intValue1;
            this.longValue1 = longValue1;
            this.floatValue1 = floatValue1;
            this.doubleValue1 = doubleValue1;
        }

        public boolean isBooleanValue1() {
            return booleanValue1;
        }
        public byte getByteValue1() {
            return byteValue1;
        }
        public short getShortValue1() {
            return shortValue1;
        }
        public int getIntValue1() {
            return intValue1;
        }
        public long getLongValue1() {
            return longValue1;
        }
        public float getFloatValue1() {
            return floatValue1;
        }
        public double getDoubleValue1() {
            return doubleValue1;
        }

        @Override
        public String toString() {
            return "AllJavaStandardTypesClass{" +
                    "" + booleanValue1 +
                    "," + byteValue1 +
                    "," + shortValue1 +
                    "," + intValue1 +
                    "," + longValue1 +
                    "," + floatValue1 +
                    "," + doubleValue1 +
                    '}';
        }
    }

    @Issue("JENKINS-31967")
    @Test
    public void testJavaStandardTypes() throws Exception {
        // check instantiate with not default values
        roundTrip(AllJavaStandardTypesClass.class, map(
                "booleanValue1", Boolean.TRUE,
                "byteValue1", Byte.MAX_VALUE,
                "shortValue1", Short.MAX_VALUE,
                "intValue1", Integer.MAX_VALUE,
                "longValue1", Long.MAX_VALUE,
                "floatValue1", Float.MAX_VALUE,
                "doubleValue1", Double.MAX_VALUE
        ));
        // check with default values
        roundTrip(AllJavaStandardTypesClass.class, map(
                "booleanValue1", false,
                "byteValue1", (byte) 0,
                "shortValue1", (short) 0,
                "intValue1", 0,
                "longValue1", (long) 0,
                "floatValue1", (float) 0.0,
                "doubleValue1", 0.0
        ));
    }

    @Test
    @Issue("JENKINS-44864")
    public void given_model_when_fieldRenamed_then_uselessSettersIgnored() throws Exception {
        EvolvedClass instance;
        Map<String,Object> expected = new HashMap<>();

        instance = new EvolvedClass(false);
        expected.clear();
        expected.put("option", Boolean.FALSE);
        assertThat("Only required properties",
                DescribableModel.uninstantiate2_(instance).toMap(), Matchers.is(expected));

        instance = new EvolvedClass(true);
        expected.clear();
        expected.put("option", Boolean.TRUE);
        assertThat("Only required properties",
                DescribableModel.uninstantiate2_(instance).toMap(), Matchers.is(expected));

        instance = new EvolvedClass(true);
        instance.setName("bob");
        expected.clear();
        expected.put("option", Boolean.TRUE);
        expected.put("name", "bob");
        assertThat("current setters are rendered",
                DescribableModel.uninstantiate2_(instance).toMap(), Matchers.is(expected));

        instance = new EvolvedClass(false);
        instance.setTitle("bill");
        expected.clear();
        expected.put("option", Boolean.FALSE);
        expected.put("name", "bill");
        assertThat("migrated setters are migrated",
                DescribableModel.uninstantiate2_(instance).toMap(), Matchers.is(expected));

        instance = new EvolvedClass(false);
        instance.setName("random");
        instance.setDescription("This is the thing");
        expected.clear();
        expected.put("option", Boolean.FALSE);
        expected.put("name", "random");
        expected.put("description", "This is the thing");
        assertThat("if the old setter mangles the value but has 1:1 mapping, we still ignore redundant setters",
                DescribableModel.uninstantiate2_(instance).toMap(), Matchers.is(expected));

        instance = new EvolvedClass(false);
        instance.setName("random");
        instance.setSummary("Legacy summary");
        expected.clear();
        expected.put("option", Boolean.FALSE);
        expected.put("name", "random");
        expected.put("description", "Prefix: Legacy summary");
        assertThat("Doesn't matter if we set the value through the old setter or the new",
                DescribableModel.uninstantiate2_(instance).toMap(), Matchers.is(expected));

        instance = new EvolvedClass(false);
        instance.setLegacyMode(53);
        expected.clear();
        expected.put("option", Boolean.FALSE);
        expected.put("legacyMode", 53);
        assertThat("A deprecated setter that produces a different object instance is retained",
                DescribableModel.uninstantiate2_(instance).toMap(), Matchers.is(expected));

    }

    public static class EvolvedClass {
        private final boolean option;
        private String name;
        private String description;
        private Integer legacyMode;

        @DataBoundConstructor
        public EvolvedClass(boolean option) {
            this.option = option;
        }

        public boolean isOption() {
            return option;
        }

        public String getName() {
            return name;
        }

        @DataBoundSetter
        public void setName(String name) {
            this.name = name;
        }

        @Deprecated
        public String getTitle() {
            return name;
        }

        @Deprecated
        @DataBoundSetter
        public void setTitle(String title) {
            this.name = title;
        }

        public String getDescription() {
            return description;
        }

        @DataBoundSetter
        public void setDescription(String description) {
            this.description = description;
        }

        @Deprecated
        public String getSummary() {
            return description == null ? null : (description.startsWith("Prefix: ") ? description.substring(8) : description);
        }

        @Deprecated
        @DataBoundSetter
        public void setSummary(String summary) {
            description = summary == null ? null : "Prefix: " + summary;
        }

        @Deprecated
        public Integer getLegacyMode() {
            return legacyMode;
        }

        @Deprecated
        @DataBoundSetter
        public void setLegacyMode(Integer legacyMode) {
            this.legacyMode = legacyMode;
        }
    }
}

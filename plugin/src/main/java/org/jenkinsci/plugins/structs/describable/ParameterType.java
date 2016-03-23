package org.jenkinsci.plugins.structs.describable;

import com.google.common.primitives.Primitives;
import org.jvnet.tiger_types.Types;

import javax.annotation.Nonnull;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A type of a parameter to a class.
 *
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public abstract class ParameterType {
    @Nonnull
    private final Type actualType;

    public Type getActualType() {
        return actualType;
    }

    ParameterType(Type actualType) {
        this.actualType = actualType;
    }

    static ParameterType of(Type type) {
        return of(type, new Stack<String>());
    }

    static ParameterType of(Type type, @Nonnull Stack<String> tracker) {
        try {
            if (type instanceof Class) {
                Class<?> c = (Class<?>) type;
                if (c == String.class || Primitives.unwrap(c).isPrimitive()) {
                    return new AtomicType(c);
                }
                if (Enum.class.isAssignableFrom(c)) {
                    List<String> constants = new ArrayList<String>();
                    for (Enum<?> value : c.asSubclass(Enum.class).getEnumConstants()) {
                        constants.add(value.name());
                    }
                    return new EnumType(c, constants.toArray(new String[constants.size()]));
                }
                if (c == URL.class) {
                    return new AtomicType(String.class);
                }
                if (c.isArray()) {
                    return new ArrayType(c);
                }
                // Assume it is a nested object of some sort.
                Set<Class<?>> subtypes = DescribableModel.findSubtypes(c);
                if ((subtypes.isEmpty() && !Modifier.isAbstract(c.getModifiers())) || subtypes.equals(Collections.singleton(c))) {
                    // Probably homogeneous. (Might be concrete but subclassable.)
                    String key = c.getName();
                    if (tracker.search(key) < 0) {
                        DescribableModel model;
                        tracker.push(key);
                        model = new DescribableModel(c, tracker);
                        tracker.pop();
                        return new HomogeneousObjectType(c, model);
                    } else {
                        return new ErrorType(new IllegalArgumentException("type that refers to itself"), type);
                    }
                } else {
                    // Definitely heterogeneous.
                    Map<String,List<Class<?>>> subtypesBySimpleName = new HashMap<String,List<Class<?>>>();
                    for (Class<?> subtype : subtypes) {
                        String simpleName = subtype.getSimpleName();
                        List<Class<?>> bySimpleName = subtypesBySimpleName.get(simpleName);
                        if (bySimpleName == null) {
                            subtypesBySimpleName.put(simpleName, bySimpleName = new ArrayList<Class<?>>());
                        }
                        bySimpleName.add(subtype);
                    }
                    Map<String,DescribableModel> types = new TreeMap<String,DescribableModel>();
                    for (Map.Entry<String,List<Class<?>>> entry : subtypesBySimpleName.entrySet()) {
                        if (entry.getValue().size() == 1) { // normal case: unambiguous via simple name
                            try {
                                String key = entry.getKey();
                                if (tracker.search(key) < 0) {
                                    tracker.push(key);
                                    types.put(key, new DescribableModel(entry.getValue().get(0), tracker));
                                    tracker.pop();
                                }
                            } catch (Exception x) {
                                LOGGER.log(Level.FINE, "skipping subtype", x);
                            }
                        } else { // have to diambiguate via FQN
                            for (Class<?> subtype : entry.getValue()) {
                                try {
                                    String name = subtype.getName();
                                    if (tracker.search(name) < 0) {
                                        tracker.push(name);
                                        types.put(name, new DescribableModel(subtype, tracker));
                                        tracker.pop();
                                    }
                                } catch (Exception x) {
                                    LOGGER.log(Level.FINE, "skipping subtype", x);
                                }
                            }
                        }
                    }
                    return new HeterogeneousObjectType(c, types);
                }
            }
            if (Types.isSubClassOf(type, Collection.class)) {
                return new ArrayType(type, of(Types.getTypeArgument(Types.getBaseClass(type,Collection.class), 0, Object.class)));
            }
            throw new UnsupportedOperationException("do not know how to categorize attributes of type " + type);
        } catch (Exception x) {
            return new ErrorType(x, type);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ParameterType.class.getName());
}

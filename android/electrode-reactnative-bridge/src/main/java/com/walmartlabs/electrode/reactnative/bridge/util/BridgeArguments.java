package com.walmartlabs.electrode.reactnative.bridge.util;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.walmartlabs.electrode.reactnative.bridge.BridgeMessage;
import com.walmartlabs.electrode.reactnative.bridge.Bridgeable;
import com.walmartlabs.electrode.reactnative.bridge.helpers.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class contains utility methods for bridge
 */

public final class BridgeArguments {

    private static final String TAG = BridgeArguments.class.getSimpleName();

    private static final Set<Class> SUPPORTED_PRIMITIVE_TYPES = new HashSet() {{
        add(String.class);
        add(String[].class);
        add(Integer.class);
        add(Integer[].class);
        add(int[].class);
        add(Boolean.class);
        add(Boolean[].class);
        add(Double.class);
        add(Double[].class);
        add(double[].class);
    }};

    /**
     * @param object Accepted object types are {@link Bridgeable}, All primitive wrappers and null
     * @return Bundle representation of the given object. The output bundle will put the object inside key = {@link BridgeMessage#BRIDGE_MSG_DATA}
     */
    @NonNull
    public static Bundle generateDataBundle(@Nullable Object object) {
        if (object == null) {
            return Bundle.EMPTY;
        }
        Bundle data = new Bundle();
        if (object instanceof Bridgeable) {
            data.putBundle(BridgeMessage.BRIDGE_MSG_DATA, ((Bridgeable) object).toBundle());
        } else if (object instanceof List) {
            List objList = (List) object;
            if (!objList.isEmpty()) {
                Object firstItem = objList.get(0);
                if (firstItem instanceof Bridgeable) {
                    Bundle[] bundleArray = getBundleArray(objList);
                    data.putParcelableArray(BridgeMessage.BRIDGE_MSG_DATA, bundleArray);
                } else if (isSupportedPrimitiveType(firstItem.getClass())) {

                    if (firstItem instanceof String) {
                        String[] primitiveArray = (String[]) objList.toArray(new String[objList.size()]);
                        data.putStringArray(BridgeMessage.BRIDGE_MSG_DATA, primitiveArray);
                    } else if (firstItem instanceof Integer) {
                        int[] primitiveArray = new int[objList.size()];
                        for (int i = 0; i < objList.size(); i++) {
                            primitiveArray[i] = (Integer) objList.get(i);
                        }
                        data.putIntArray(BridgeMessage.BRIDGE_MSG_DATA, primitiveArray);
                    } else {
                        throw new IllegalArgumentException("Should never happen, looks like logic to handle " + firstItem.getClass() + " is not implemented yet");
                    }
                } else {
                    throw new IllegalArgumentException("should never reach here");
                }

            } else {
                Logger.w(TAG, "Received empty list, will return empty bundle");
            }
        } else {
            data = BridgeArguments.getBundleForPrimitive(object, object.getClass());
        }

        return data;
    }

    @NonNull
    private static Bundle[] getBundleArray(@NonNull List<Bridgeable> objList) {
        Bundle[] bundleList = new Bundle[objList.size()];
        for (int i = 0; i < objList.size(); i++) {
            Object obj = objList.get(i);
            bundleList[i] = (((Bridgeable) obj).toBundle());
        }
        return bundleList;
    }

    /**
     * Looks for an entry with key = {@link BridgeMessage#BRIDGE_MSG_DATA} inside the bundle and then tries to convert the value to either a primitive wrapper or {@link Bridgeable}
     *
     * @param payload     {@link Bundle}
     * @param returnClass {@link Class}
     * @param <T>         return type
     * @return T
     */
    @Nullable
    public static <T> Object generateObject(@Nullable Bundle payload, @NonNull Class<T> returnClass) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        Object data = payload.get(BridgeMessage.BRIDGE_MSG_DATA);
        if (data == null) {
            Logger.d(TAG, "Cannot find key(%s) in given bundle:%s", BridgeMessage.BRIDGE_MSG_DATA, payload);
            return null;
        }

        Object response;
        if (isArray(data)) {
            response = getList(data, returnClass);
        } else if (data instanceof Bundle) {
            response = BridgeArguments.bridgeableFromBundle((Bundle) data, returnClass);
        } else if (returnClass.isAssignableFrom(data.getClass())//GOTCHA: we should check for return data being an array of primitives. this is needed because the returnClass ony contains the content of the array.
                && isSupportedPrimitiveType(data.getClass())) {
            //noinspection unchecked
            response = data;
        } else {
            throw new IllegalArgumentException("Should never happen, looks like logic to handle " + data.getClass() + " type is not implemented yet");
        }
        return response;
    }


    private static boolean isArray(@NonNull Object obj) {
        return obj.getClass().isArray();
    }

    /**
     * @param obj           input array
     * @param listItemClass Defines the conent type of the list
     * @return List
     */
    private static List getList(Object obj, @Nullable Class listItemClass) {
        if (!isArray(obj)) {
            throw new IllegalArgumentException("Should never reach here, expected an array, received: " + obj);
        }

        List convertedList = new ArrayList<>();
        if (obj instanceof Bundle[]) {
            if (listItemClass == null) {
                throw new IllegalArgumentException("listItemClass is required to convert Bundle[]");
            }
            Bundle[] bundles = (Bundle[]) obj;

            for (Bundle bundle : bundles) {
                Object item = BridgeArguments.bridgeableFromBundle(bundle, listItemClass);
                convertedList.add(item);
            }

        } else if (Object[].class.isAssignableFrom(obj.getClass())
                && isSupportedPrimitiveType(obj.getClass())) {
            Object[] objectArray = (Object[]) obj;
            for (Object o : objectArray) {
                convertedList.add(o);
            }
        } else if (int[].class.isAssignableFrom(obj.getClass())
                && isSupportedPrimitiveType(obj.getClass())) {
            int[] objectArray = (int[]) obj;
            for (Object o : objectArray) {
                convertedList.add(o);
            }

        } else if (double[].class.isAssignableFrom(obj.getClass())
                && isSupportedPrimitiveType(obj.getClass())) {
            double[] objectArray = (double[]) obj;
            for (Object o : objectArray) {
                convertedList.add(o);
            }

        } else {
            throw new IllegalArgumentException("Array of type " + obj.getClass().getSimpleName() + " is not supported yet");
        }
        return convertedList;
    }

    @VisibleForTesting
    @NonNull
    static <T> T bridgeableFromBundle(@NonNull Bundle bundle, @NonNull Class<T> clazz) {
        Logger.d(TAG, "entering bridgeableFromBundle with bundle(%s) for class(%s)", bundle, clazz);

        //noinspection TryWithIdenticalCatches
        try {
            Class clz = Class.forName(clazz.getName());
            Constructor[] constructors = clz.getDeclaredConstructors();
            for (Constructor constructor : constructors) {
                if (constructor.getParameterTypes().length == 1) {
                    if (constructor.getParameterTypes()[0].isInstance(bundle)) {
                        Object[] args = new Object[1];
                        args[0] = bundle;
                        Object result = constructor.newInstance(args);
                        if (clazz.isInstance(result)) {
                            //noinspection unchecked
                            return (T) result;
                        } else {
                            Logger.w(TAG, "Object creation from bundle not possible since the created object(%s) is not an instance of %s", result, clazz);
                        }
                    }
                    //Empty constructor available, object construction using bundle can now be attempted.
                    break;
                }
            }
            Logger.w(TAG, "Could not find a constructor that takes in a Bundle param for class(%s)", clazz);
        } catch (ClassNotFoundException e) {
            logException(e);
        } catch (@SuppressWarnings("TryWithIdenticalCatches") InstantiationException e) {
            logException(e);
        } catch (IllegalAccessException e) {
            logException(e);
        } catch (InvocationTargetException e) {
            logException(e);
        }

        throw new IllegalArgumentException("Unable to generate a Bridgeable from bundle: " + bundle);
    }

    private static void logException(Exception e) {
        Logger.w(TAG, "FromBundle failed to execute(%s)", e.getMessage() != null ? e.getMessage() : e.getCause());
    }

    @NonNull
    @VisibleForTesting
    static Bundle getBundleForPrimitive(@NonNull Object respObj, @NonNull Class respClass) {
        Bundle bundle = new Bundle();
        String key = BridgeMessage.BRIDGE_MSG_DATA;
        if (String.class.isAssignableFrom(respClass)) {
            bundle.putString(key, (String) respObj);
        } else if (Integer.class.isAssignableFrom(respClass)) {
            bundle.putInt(key, (Integer) respObj);
        } else if (Boolean.class.isAssignableFrom(respClass)) {
            bundle.putBoolean(key, (Boolean) respObj);
        } else if (String[].class.isAssignableFrom(respClass)) {
            bundle.putStringArray(key, (String[]) respObj);
        } else {
            throw new IllegalArgumentException("Should never happen, looks like logic to handle " + respClass + " is not implemented yet");
        }
        return bundle;
    }

    public static Number getNumberValue(@NonNull Bundle bundle, String key) {
        Number output = null;
        if (bundle != null && bundle.containsKey(key)) {
            Object obj = bundle.get(key);
            if (obj != null) {
                if (obj.getClass().isAssignableFrom(Integer.class)) {
                    output = bundle.getInt(key);
                } else if (obj.getClass().isAssignableFrom(Double.class)) {
                    output = bundle.getDouble(key);
                }
            }
        }
        return output;
    }

    private static boolean isSupportedPrimitiveType(@NonNull Class clazz) {
        return SUPPORTED_PRIMITIVE_TYPES.contains(clazz);
    }
}

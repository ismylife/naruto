/*
 * Copyright (c) 2013. wyouflf (wyouflf@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lidroid.xutils;

import android.app.Activity;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.view.View;
import com.lidroid.xutils.util.LogUtils;
import com.lidroid.xutils.view.EventListenerManager;
import com.lidroid.xutils.view.ResLoader;
import com.lidroid.xutils.view.ViewFinder;
import com.lidroid.xutils.view.ViewInjectInfo;
import com.lidroid.xutils.view.annotation.ContentView;
import com.lidroid.xutils.view.annotation.PreferenceInject;
import com.lidroid.xutils.view.annotation.ResInject;
import com.lidroid.xutils.view.annotation.ViewInject;
import com.lidroid.xutils.view.annotation.event.EventBase;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ViewUtils {

    private ViewUtils() {
    }

    //注入
    public static void inject(View view) {
        injectObject(view, new ViewFinder(view));
    }

    public static void inject(Activity activity) {
        injectObject(activity, new ViewFinder(activity));
    }

    public static void inject(PreferenceActivity preferenceActivity) {
        injectObject(preferenceActivity, new ViewFinder(preferenceActivity));
    }

    public static void inject(Object handler, View view) {
        injectObject(handler, new ViewFinder(view));
    }

    public static void inject(Object handler, Activity activity) {
        injectObject(handler, new ViewFinder(activity));
    }

    public static void inject(Object handler, PreferenceGroup preferenceGroup) {
        injectObject(handler, new ViewFinder(preferenceGroup));
    }

    public static void inject(Object handler, PreferenceActivity preferenceActivity) {
        injectObject(handler, new ViewFinder(preferenceActivity));
    }

    //利用反射.注入对象.一个Activity不必再在onCreate()方法中调用setContentView(int)方法了
    private static void injectObject(Object handler, ViewFinder finder) {

    	//反射得到对象的字节码
        Class<?> handlerType = handler.getClass();

        // inject ContentView.根据字节码得到该类上的ContentView注解
        ContentView contentView = handlerType.getAnnotation(ContentView.class);
        if (contentView != null) {
            try {
            	//反射得到该类的上的setContentView()方法.
                Method setContentViewMethod = handlerType.getMethod("setContentView", int.class);
                //执行setContentView()方法.
                //相当于执行:handler.setContentView(contentView.value())方法.
                setContentViewMethod.invoke(handler, contentView.value());
            } catch (Throwable e) {
                LogUtils.e(e.getMessage(), e);
            }
        }

        // inject view:遍历所有属性
        Field[] fields = handlerType.getDeclaredFields();
        if (fields != null && fields.length > 0) {
            for (Field field : fields) {
            	//得到ViewInject注解
                ViewInject viewInject = field.getAnnotation(ViewInject.class);
                if (viewInject != null) {
                    try {
                        View view = finder.findViewById(viewInject.value(), viewInject.parentId());
                        if (view != null) {
                        	//暴力反射
                            field.setAccessible(true);
                            //该方法效果等同于handler.field = view
                            //MainActivity.Button=view....
                            field.set(handler, view);
                        }
                    } catch (Throwable e) {
                        LogUtils.e(e.getMessage(), e);
                    }
                } else {
                	//得到字段对应的资源注解
                    ResInject resInject = field.getAnnotation(ResInject.class);
                    if (resInject != null) {
                        try {
                        	//加载不同类型的资源:①.资源类型;②.上下文;③.资源id.
                            Object res = ResLoader.loadRes(
                                    resInject.type(), finder.getContext(), resInject.id());
                            if (res != null) {
                                field.setAccessible(true);
                                //把资源设置到属性上.等同于:object.fieldName = value 
                                field.set(handler, res);
                            }
                        } catch (Throwable e) {
                            LogUtils.e(e.getMessage(), e);
                        }
                    } else {
                    	//得到PreferenceInject注解.
                        PreferenceInject preferenceInject = field.getAnnotation(PreferenceInject.class);
                        if (preferenceInject != null) {
                            try {
                            	//设置PreferenceActivity,创建各种持久化存储的控件状态.
                                Preference preference = finder.findPreference(preferenceInject.value());
                                if (preference != null) {
                                    field.setAccessible(true);
                                    field.set(handler, preference);
                                }
                            } catch (Throwable e) {
                                LogUtils.e(e.getMessage(), e);
                            }
                        }
                    }
                }
            }
        }

        // inject event:得到字节码中的各个方法
        Method[] methods = handlerType.getDeclaredMethods();
        if (methods != null && methods.length > 0) {
            for (Method method : methods) {
            	//得到注解方法
                Annotation[] annotations = method.getDeclaredAnnotations();
                if (annotations != null && annotations.length > 0) {
                    for (Annotation annotation : annotations) {
                    	//得到注解类型
                        Class<?> annType = annotation.annotationType();
                        if (annType.getAnnotation(EventBase.class) != null) {
                            method.setAccessible(true);
                            try {
                                // ProGuard：-keep class * extends java.lang.annotation.Annotation { *; }
                                //得到注解类中的value()方法.
                            	Method valueMethod = annType.getDeclaredMethod("value");
                                Method parentIdMethod = null;
                                try {
                                	//得到注解中的parentId()方法
                                    parentIdMethod = annType.getDeclaredMethod("parentId");
                                } catch (Throwable e) {
                                }
                                //执行注解方法
                                Object values = valueMethod.invoke(annotation);
                                Object parentIds = parentIdMethod == null ? null : parentIdMethod.invoke(annotation);
                                int parentIdsLen = parentIds == null ? 0 : Array.getLength(parentIds);
                                int len = Array.getLength(values);
                                for (int i = 0; i < len; i++) {
                                    ViewInjectInfo info = new ViewInjectInfo();
                                    info.value = Array.get(values, i);
                                    info.parentId = parentIdsLen > i ? (Integer) Array.get(parentIds, i) : 0;
                                    EventListenerManager.addEventMethod(finder, info, annotation, handler, method);
                                }
                            } catch (Throwable e) {
                                LogUtils.e(e.getMessage(), e);
                            }
                        }
                    }
                }
            }
        }
    }

}

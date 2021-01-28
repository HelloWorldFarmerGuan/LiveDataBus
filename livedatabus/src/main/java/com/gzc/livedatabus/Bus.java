package com.gzc.livedatabus;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

final class Bus {
    //存放订阅者
    private Map<String, BusMutableLiveData<Object>> busMap;

    //单例
    private static Bus bus =new Bus();
    private Bus(){
        busMap =new HashMap<>();
    }
    public static Bus getInstance(){
        return bus;
    }

    /**
     * 可以不写中的type，默认为上面Object.class，但是接受数据的时候需要强制转换
     * @param key
     * @param type
     * @param sticky
     * @param <T>
     * @return
     */
    public synchronized<T> BusMutableLiveData<T> with(String key,Class<T>type,boolean sticky){
        if(!busMap.containsKey(key)){
            busMap.put(key,new BusMutableLiveData<Object>());
        }
        BusMutableLiveData<T> busMutableLiveData = (BusMutableLiveData<T>) busMap.get(key);
        busMutableLiveData.setSticky(sticky);
        return busMutableLiveData;
    }

    public synchronized BusMutableLiveData<Object> with(String key,boolean sticky){
        return with(key,Object.class,sticky);
    }

    public synchronized BusMutableLiveData<Object>setKey(String key){
        if(!busMap.containsKey(key)){
            busMap.put(key,new BusMutableLiveData<Object>());
        }
        BusMutableLiveData busMutableLiveData = (BusMutableLiveData) busMap.get(key);
        return busMutableLiveData;
    }

    public static class BusMutableLiveData<T> extends MutableLiveData<T> {
        private boolean sticky;
        public void setSticky(boolean sticky){
            this.sticky = sticky;
        }

        @Override
        protected void onActive() {
            super.onActive();
        }

        /**
         * onPause ，onStop， onDestroy 的时候，都可以回调 obsever 的 onChange 方法
         * 注意：必须手动 remove obsever
         * @param observer
         */
        @Override
        public void observeForever(@NonNull Observer<? super T> observer) {
            super.observeForever(observer);
            if(!sticky){
                hook(observer);
            }
        }

        @Override
        public void removeObserver(@NonNull Observer<? super T> observer) {
            super.removeObserver(observer);
        }

        @Override
        public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
            super.observe(owner, observer);
            if(!sticky) {
                hook(observer);
            }
        }

        private void hook(Observer<? super T> observer) {
            try{
                //1.得到mLastVersion
                Class<LiveData> liveDataClass= LiveData.class;
                Field mObserversField = liveDataClass.getDeclaredField("mObservers");
                mObserversField.setAccessible(true);
                //获取到这个成员变量对应的对象
                Object mObserversObject = mObserversField.get(this);
                //得到map
                Class<?> mObserversObjectClass = mObserversObject.getClass();
                //获取到mObservers对象的get方法
                Method get=mObserversObjectClass.getDeclaredMethod("get",Object.class);
                get.setAccessible(true);
                //执行get方法
                Object invokeEntry=get.invoke(mObserversObject,observer);
                //取到map中的value
                Object observerWraper=null;
                if(invokeEntry!=null && invokeEntry instanceof Map.Entry){
                    observerWraper=((Map.Entry)invokeEntry).getValue();
                }
                if(observerWraper==null){
                    throw new NullPointerException("observerWraper is null");
                }
                //得到ObserverWrapper的类对象
                Class<?> superclass=observerWraper.getClass().getSuperclass();
                Field mLastVersion = superclass.getDeclaredField("mLastVersion");
                mLastVersion.setAccessible(true);


                //2.得到mVersion
                Field mVersion = liveDataClass.getDeclaredField("mVersion");
                mVersion.setAccessible(true);

                //3.把mVersion的值填入到mLastVersion中
                Object mVersionValue=mVersion.get(this);
                mLastVersion.set(observerWraper,mVersionValue);

            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

}



















package com.ww7h.ww.common.apis.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import org.greenrobot.greendao.AbstractDaoMaster;
import org.greenrobot.greendao.AbstractDaoSession;
import org.greenrobot.greendao.database.DatabaseOpenHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;


public class GreenDaoManager {

    private AbstractDaoMaster daoMaster = null;
    private AbstractDaoSession daoSession = null;
    private DatabaseOpenHelper helper = null;
    private SQLiteDatabase mRDb;
    private SQLiteDatabase mWDb;

    private int openCount = 0;

    private final int WRITE_TYPE_INSERT = 0;
    private final int WRITE_TYPE_EXECUTE = 1;
    private final int WRITE_TYPE_EXECUTE_ALL = 2;
    private final int WRITE_TYPE_DELETE = 3;


    public String getDbPath() {
        if (helper == null) {
            throw new NullPointerException("GreenDaoManager未初始化，请调用initGreenDao初始化");
        }
        return helper.getReadableDatabase().getPath();
    }

    public static GreenDaoManager getInstance() {
        return GreenDaoManagerHelper.INSTANCE;
    }

    private static class GreenDaoManagerHelper {
        private static final GreenDaoManager INSTANCE = new GreenDaoManager();
    }

    private GreenDaoManager() {

    }

    public void initGreenDao(DatabaseOpenHelper helper, AbstractDaoMaster daoMaster) {
        this.helper = helper;
        this.daoMaster = daoMaster;
    }


    /**
     * 获取数据库操作对象
     *
     * @param needWrite 是否需要写入操作
     * @return 数据库操作对象
     */
    private synchronized void getDB(Boolean needWrite) {
        if (needWrite) {
            mWDb = helper.getWritableDatabase();
        } else {
            mRDb = helper.getReadableDatabase();
        }
    }

    /**
     * 关闭数据库
     *
     * @param db 数据库对象
     */
    public void closeDB(SQLiteDatabase db) {
        db.close();
        mWDb = null;
        mRDb = null;
    }

    /**
     * 关闭数据库
     *
     */
    public void closeAllDb() {
        if (mWDb != null && mWDb.isOpen()) {
            mWDb.close();
        }
        if (mRDb != null && mRDb.isOpen()) {
            mRDb.close();
        }
        mWDb = null;
        mRDb = null;
    }

    /**
     * 关闭数据库
     */
    public void closeDB() {
        daoSession.getDatabase().close();
    }

    private AbstractDaoSession getDaoSession() {
        daoSession = daoMaster.newSession();
        return daoSession;
    }


    public <T> void insertOrReplace(T entity) {
        writeDB(0, entity, null, null, null);
    }

    public <T> void insertOrReplaceList(List<T> entityList) {
        for (T t : entityList) {
            insertOrReplace(t);
        }
    }

    public <T> void insertOrReplaceArray(T[] entityArray) {
        for (T t : entityArray) {
            insertOrReplace(t);
        }
    }

    public <T> void deleteOne(T entity) {
        writeDB(WRITE_TYPE_DELETE, entity, null, null, null);
    }

    public <T> void deleteList(List<T> entityList) {
        for (T t : entityList) {
            writeDB(WRITE_TYPE_DELETE, t, null, null, null);
        }
    }

    public <T> void deleteArray(T[] entityList) {
        for (T t : entityList) {
            writeDB(WRITE_TYPE_DELETE, t, null, null, null);
        }
    }

    public void executeSql(String sql) {
        writeDB(WRITE_TYPE_EXECUTE, null, null, null, sql);
    }

    public void executeSqlList(List<String> sqlList) {
        writeDB(WRITE_TYPE_EXECUTE_ALL, null, sqlList, null, null);
    }

    public void executeSqlArray(String[] sqlArray) {
        writeDB(WRITE_TYPE_EXECUTE_ALL, null, null, sqlArray, null);
    }

    private synchronized <T> void writeDB(int type, T entity, List<String> sqlList, String[] sqls, String sql) {

        settingDb(true);
        openCount++;
        switch (type) {
            case WRITE_TYPE_INSERT:
                getDaoSession().insertOrReplace(entity);
                break;
            case WRITE_TYPE_EXECUTE:
                getDaoSession().getDatabase().execSQL(sql);
                break;
            case WRITE_TYPE_EXECUTE_ALL:
                executeSqlList(sqlList, sqls);
                break;
            case WRITE_TYPE_DELETE:
                getDaoSession().delete(entity);
                break;
        }
    }

    public <T> T queryOne(Class<T> clazz, String sql) {
        List<T> tList = queryList(clazz, sql);
        if (!tList.isEmpty()) {
            return tList.get(0);
        } else {
            return null;
        }
    }

    public <T> void queryOne(final Class<T> clazz, final String sql, final GreenDaoCallBack.QueryCallBack<T> callBack) {

        Observable.create(new ObservableOnSubscribe<T>() {
            @Override
            public void subscribe(ObservableEmitter<T> emitter) {
                List<T> tList = queryList(clazz, sql);
                if (!tList.isEmpty()) {
                    emitter.onNext(tList.get(0));
                } else {
                    emitter.onError(new Throwable("没有查询到数据"));
                }

            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<T>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(T t) {
                        callBack.querySuccess(t);

                    }

                    @Override
                    public void onError(Throwable e) {
                        callBack.queryFail(e.toString());
                    }

                    @Override
                    public void onComplete() {

                    }

                });

    }

    public <T> void queryList(final Class<T> clazz, final String sql, final GreenDaoCallBack.QueryCallBack<List<T>> callBack) {

        Observable.create(new ObservableOnSubscribe<List<T>>() {
            @Override
            public void subscribe(ObservableEmitter<List<T>> emitter) throws Exception {
                emitter.onNext(queryList(clazz, sql));
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<T>>() {
                    @Override
                    public void accept(List<T> tList) throws Exception {
                        callBack.querySuccess(tList);
                    }
                });

    }

    public  <T> List<T> queryList(Class<T> clazz, String sql) {
        settingDb(false);
        List<T> tList = new ArrayList<T>();
        Cursor cursor = null;
        try {
            cursor = mRDb.rawQuery(sql, null);
        } catch (Exception e) {
            return tList;
        }

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            T t;
            try {
                t = clazz.newInstance();
            } catch (InstantiationException e1) {
                e1.printStackTrace();
                cursor.moveToNext();
                continue;
            } catch (IllegalAccessException e1) {
                e1.printStackTrace();
                continue;
            }

            String[] names = cursor.getColumnNames();
            for (String name : names) {
                setFieldValue(t, name, cursor, clazz);
            }
            tList.add(t);
            cursor.moveToNext();
        }
        cursor.close();
        return tList;
    }

    private <T> void setFieldValue(T t, String name, Cursor cursor, Class<T> clazz) {
        String fieldName = name;
        int index = cursor.getColumnIndex(fieldName);
        Field field = null;
        try {
            fieldName = fieldName.equals("_id") ? "id" : fieldName;
            try {
                field = clazz.getDeclaredField(fieldName);
            } catch (Exception e) {
                Field[] fields = clazz.getDeclaredFields();
                for (Field f : fields) {
                    if (f.getName().toLowerCase().equals(fieldName.toLowerCase())) {
                        field = f;
                        break;
                    }
                }
                if (field == null) {
                    return;
                }
            }

            field.setAccessible(true);
            Class<?> type = field.getType();
            switch (type.getName()) {
                case "long":
                    field.set(t, cursor.getLong(index));
                    break;
                case "java.lang.String":
                    field.set(t, cursor.getString(index));
                    break;
                case "java.lang.Long":
                    field.set(t, cursor.getLong(index));
                    break;
                case "int":
                    field.set(t, cursor.getInt(index));
                    break;
                case "java.lang.Integer":
                    field.set(t, cursor.getInt(index));
                    break;
                case "double":
                    field.set(t, cursor.getDouble(index));
                    break;
                case "java.lang.Double":
                    field.set(t, cursor.getDouble(index));
                    break;
            }
        } catch (IllegalAccessException | IllegalArgumentException e) {
            e.printStackTrace();
        }

    }


    private void executeSqlList(final List<String> sqlList, final String[] sqls) {
        if ((sqlList == null || sqlList.isEmpty()) && (sqls == null || sqls.length == 0)) {
            return;
        }
        getDaoSession().runInTx(new Runnable() {
            @Override
            public void run() {
                if (sqlList != null) {
                    for (String sql : sqlList) {
                        getDaoSession().getDatabase().execSQL(sql);
                    }
                }
                if (sqls != null) {
                    for (String sql : sqls) {
                        getDaoSession().getDatabase().execSQL(sql);
                    }
                }
            }
        });
    }

    /**
     * 判断数据库中某张表是否存在
     */
    public Boolean sqlTableIsExist(String tableName) {
        long count = queryCount("select count(*) as c from sqlite_master  where type ='table' and name ='" + tableName + "'");
        return count > 0;
    }

    public long queryCount(String sql) {
        settingRDb();
        Cursor cursor = mRDb.rawQuery(sql, null);
        long count = 0;
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            String[] names = cursor.getColumnNames();
            count = cursor.getLong(cursor.getColumnIndex(names[0]));
            cursor.moveToNext();
        }
        cursor.close();
        return count;
    }


    private synchronized void settingDb(boolean isWrite) {
        if (isWrite) {
            settingWDb();
        } else {
            settingRDb();
        }

    }

    private void settingRDb() {
        if (mRDb == null) {
            if (mWDb != null) {
                mRDb = mWDb;
            } else {
                getDB(false);
            }
        }
    }

    private void settingWDb() {
        if (mWDb == null) {
            getDB(true);
            mRDb = mWDb;
        }
    }

}

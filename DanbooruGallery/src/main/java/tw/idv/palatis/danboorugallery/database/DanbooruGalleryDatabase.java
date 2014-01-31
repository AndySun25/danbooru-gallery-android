////////////////////////////////////////////////////////////////////////////////
// Danbooru Gallery Android - an danbooru-style imageboard browser
//     Copyright (C) 2014  Victor Tseng
//
//     This program is free software: you can redistribute it and/or modify
//     it under the terms of the GNU General Public License as published by
//     the Free Software Foundation, either version 3 of the License, or
//     (at your option) any later version.
//
//     This program is distributed in the hope that it will be useful,
//     but WITHOUT ANY WARRANTY; without even the implied warranty of
//     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//     GNU General Public License for more details.
//
//     You should have received a copy of the GNU General Public License
//     along with this program. If not, see <http://www.gnu.org/licenses/>
////////////////////////////////////////////////////////////////////////////////

package tw.idv.palatis.danboorugallery.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import tw.idv.palatis.danboorugallery.model.Host;
import tw.idv.palatis.danboorugallery.model.Post;
import tw.idv.palatis.danboorugallery.model.Tag;

public class DanbooruGalleryDatabase
{
    private static final String TAG = "DanbooruGalleryDatabase";

    public static final String MEMORY_DATABASE_NAME = "temp";
    public static final String MAIN_DATABASE_NAME = "main";

    private static SQLiteDatabase sDatabase;

    public static void init(Context context)
    {
        DatabaseOpenHelper helper = new DatabaseOpenHelper(context);
        sDatabase = helper.getWritableDatabase();
        HostsTable.init(sDatabase);
        PostsTable.init(sDatabase);
        TagsTable.init(sDatabase);
        PostTagsLinkTable.init(sDatabase);
        PostTagsView.init(sDatabase);
    }

    private static class DatabaseOpenHelper
        extends SQLiteOpenHelper
    {
        private static final String TAG = "DanbooruGalleryDatabaseOpenHelper";

        private static final int DATABASE_VERSION = 3;
        private static final String DATABASE_NAME = "DanbooruGalleryDatabase.db";

        // Persistent
        private static final String SQL_CREATE_TABLE_HOSTS =
            "CREATE TABLE IF NOT EXISTS " + Host.MAIN_TABLE_NAME + " (" +
                Host.KEY_HOST_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                Host.KEY_HOST_ENABLED + " INTEGER NOT NULL," +
                Host.KEY_HOST_NAME + " TEXT NOT NULL," +
                Host.KEY_HOST_URL + " TEXT NOT NULL," +
                Host.KEY_HOST_API +" INTEGER NOT NULL," +
                Host.KEY_HOST_LOGIN + " TEXT NOT NULL," +
                Host.KEY_HOST_PASSWORD + " TEXT NOT NULL," +
                Host.KEY_HOST_PAGE_LIMIT_STRICT + " INTEGER NOT NULL," +
                Host.KEY_HOST_PAGE_LIMIT_RELAXED + " INTEGER NOT NULL" +
            ");";
        private static final String SQL_CREATE_TABLE_POSTS =
            "CREATE TABLE IF NOT EXISTS " + Post.MAIN_TABLE_NAME + " (" +
                Post.KEY_POST_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                Post.KEY_POST_UPDATED_AT + " DATETIME NOT NULL," +
                Post.KEY_POST_CREATED_AT + " DATETIME NOT NULL," +
                Post.KEY_POST_HOST_ID + " INTEGER NOT NULL," +
                Post.KEY_POST_ID + " INTEGER NOT NULL," +
                Post.KEY_POST_FILE_SIZE + " INTEGER NOT NULL," +
                Post.KEY_POST_IMAGE_WIDTH + " INTEGER NOT NULL," +
                Post.KEY_POST_IMAGE_HEIGHT + " INTEGER NOT NULL," +
                Post.KEY_POST_FILE_URL + " TEXT NOT NULL," +
                Post.KEY_POST_LARGE_FILE_URL + " TEXT NOT NULL," +
                Post.KEY_POST_PREVIEW_FILE_URL + " TEXT NOT NULL," +
                Post.KEY_POST_RATING + " TEXT NOT NULL," +
                Post.KEY_POST_EXTRA_INFO + " TEXT NOT NULL" +
            ");";
        private static final String SQL_CREATE_TABLE_TAGS =
            "CREATE TABLE IF NOT EXISTS " + Tag.MAIN_TABLE_NAME + " (" +
                Tag.KEY_TAG_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                Tag.KEY_TAG_HASHCODE + " INTEGER UNIQUE NOT NULL," +
                Tag.KEY_TAG_NAME + " TEXT NOT NULL" +
            ");";
        private static final String SQL_CREATE_TABLE_POST_TAGS_LINK =
            "CREATE TABLE IF NOT EXISTS " + PostTagsLinkTable.TABLE_NAME + " (" +
                PostTagsLinkTable.KEY_LINK_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                PostTagsLinkTable.KEY_POST_DATABASE_ID + " INTEGER NOT NULL," +
                PostTagsLinkTable.KEY_TAG_HASHCODE + " INTEGER NOT NULL" +
            ");";
        private static final String SQL_CREATE_VIEW_POST_TAGS =
            "CREATE VIEW IF NOT EXISTS " + PostTagsView.VIEW_NAME + " AS " +
            "SELECT " +
                Post.MAIN_TABLE_NAME + "." + Post.KEY_POST_DATABASE_ID + " AS " + PostTagsView.KEY_POST_DATABASE_ID + "," +
                Tag.MAIN_TABLE_NAME + "." + Tag.KEY_TAG_NAME + " AS " + PostTagsView.KEY_TAG_NAME + " " +
            "FROM " +
                Post.MAIN_TABLE_NAME + "," + Tag.MAIN_TABLE_NAME + "," + PostTagsLinkTable.TABLE_NAME + " " +
            "WHERE " +
                PostTagsLinkTable.MAIN_TABLE_NAME + "." + PostTagsLinkTable.KEY_POST_DATABASE_ID + " == " + Post.MAIN_TABLE_NAME + "." + Post.KEY_POST_DATABASE_ID + " AND " +
                PostTagsLinkTable.MAIN_TABLE_NAME + "." + PostTagsLinkTable.KEY_TAG_HASHCODE + " == " + Tag.MAIN_TABLE_NAME + "." + Tag.KEY_TAG_HASHCODE +
            ";";
        private static final String SQL_CREATE_INDEX_POSTS_CREATED_AT =
            "CREATE INDEX IF NOT EXISTS " +
                Post.MAIN_TABLE_NAME + "__" + Post.KEY_POST_CREATED_AT + " " +
            "ON " + Post.TABLE_NAME + " (" +
                Post.KEY_POST_CREATED_AT +
            ");";

        // Temporary
        private static final String SQL_CREATE_MEMORY_TABLE_POSTS =
            "CREATE TABLE IF NOT EXISTS " + Post.MEMORY_TABLE_NAME + " (" +
                Post.KEY_POST_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                Post.KEY_POST_UPDATED_AT + " DATETIME NOT NULL," +
                Post.KEY_POST_CREATED_AT + " DATETIME NOT NULL," +
                Post.KEY_POST_HOST_ID + " INTEGER NOT NULL," +
                Post.KEY_POST_ID + " INTEGER NOT NULL," +
                Post.KEY_POST_FILE_SIZE + " INTEGER NOT NULL," +
                Post.KEY_POST_IMAGE_WIDTH + " INTEGER NOT NULL," +
                Post.KEY_POST_IMAGE_HEIGHT + " INTEGER NOT NULL," +
                Post.KEY_POST_FILE_URL + " TEXT NOT NULL," +
                Post.KEY_POST_LARGE_FILE_URL + " TEXT NOT NULL," +
                Post.KEY_POST_PREVIEW_FILE_URL + " TEXT NOT NULL," +
                Post.KEY_POST_RATING + " TEXT NOT NULL," +
                Post.KEY_POST_EXTRA_INFO + " TEXT NOT NULL" +
            ");";

        public DatabaseOpenHelper(Context context)
        {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onOpen(SQLiteDatabase db)
        {
            super.onOpen(db);
            db.beginTransactionNonExclusive();
            try
            {
                db.execSQL(SQL_CREATE_MEMORY_TABLE_POSTS);
                db.setTransactionSuccessful();
            }
            finally
            {
                db.endTransaction();
            }
        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            db.beginTransaction();
            Log.v(TAG, "Creating: " + SQL_CREATE_TABLE_HOSTS);
            db.execSQL(SQL_CREATE_TABLE_HOSTS);
            Log.v(TAG, "Creating: " + SQL_CREATE_TABLE_POSTS);
            db.execSQL(SQL_CREATE_TABLE_POSTS);
            Log.v(TAG, "Creating: " + SQL_CREATE_TABLE_TAGS);
            db.execSQL(SQL_CREATE_TABLE_TAGS);
            Log.v(TAG, "Creating: " + SQL_CREATE_TABLE_POST_TAGS_LINK);
            db.execSQL(SQL_CREATE_TABLE_POST_TAGS_LINK);
            Log.v(TAG, "Creating: " + SQL_CREATE_VIEW_POST_TAGS);
            db.execSQL(SQL_CREATE_VIEW_POST_TAGS);
            Log.v(TAG, "Creating: " + SQL_CREATE_INDEX_POSTS_CREATED_AT);
            db.execSQL(SQL_CREATE_INDEX_POSTS_CREATED_AT);

            db.setTransactionSuccessful();
            db.endTransaction();
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            Log.v(TAG, String.format("Upgrading from version %d to %d.", oldVersion, newVersion));
            onCreate(db);
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            Log.v(TAG, String.format("Downgrading from version %d to %d.", oldVersion, newVersion));
            db.execSQL("DROP TABLE IF EXISTS " + Host.MAIN_TABLE_NAME + ";");
            db.execSQL("DROP TABLE IF EXISTS " + Post.MAIN_TABLE_NAME + ";");
            db.execSQL("DROP TABLE IF EXISTS " + Tag.MAIN_TABLE_NAME + ";");
            db.execSQL("DROP TABLE IF EXISTS " + PostTagsLinkTable.TABLE_NAME + ";");
            db.execSQL("DROP INDEX IF EXISTS " + Post.MAIN_TABLE_NAME + "__" + Post.KEY_POST_HOST_ID + "__" + Post.KEY_POST_ID + ";");
            onCreate(db);
        }
    }
}

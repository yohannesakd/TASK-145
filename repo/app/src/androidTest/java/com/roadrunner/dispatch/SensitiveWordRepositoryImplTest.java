package com.roadrunner.dispatch;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.db.dao.SensitiveWordDao;
import com.roadrunner.dispatch.infrastructure.repository.SensitiveWordRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SensitiveWordRepositoryImplTest {

    private AppDatabase db;
    private SensitiveWordDao sensitiveWordDao;
    private SensitiveWordRepositoryImpl repo;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        sensitiveWordDao = db.sensitiveWordDao();
        repo = new SensitiveWordRepositoryImpl(sensitiveWordDao);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -----------------------------------------------------------------------
    // Add and list
    // -----------------------------------------------------------------------

    @Test
    public void addWord_and_getAllWords() {
        repo.addWord("badword", false);
        repo.addWord("terrible", false);
        List<String> words = repo.getAllWords();
        assertEquals(2, words.size());
        assertTrue(words.contains("badword"));
        assertTrue(words.contains("terrible"));
    }

    @Test
    public void addWord_zeroTolerance_appearsInBothLists() {
        repo.addWord("extremeword", true);
        List<String> all = repo.getAllWords();
        List<String> zt = repo.getZeroToleranceWords();
        assertEquals(1, all.size());
        assertEquals(1, zt.size());
        assertEquals("extremeword", zt.get(0));
    }

    @Test
    public void getZeroToleranceWords_excludesNonZeroTolerance() {
        repo.addWord("mild", false);
        repo.addWord("severe", true);
        List<String> zt = repo.getZeroToleranceWords();
        assertEquals(1, zt.size());
        assertEquals("severe", zt.get(0));
    }

    @Test
    public void getAllWords_includesBothTypes() {
        repo.addWord("word1", false);
        repo.addWord("word2", true);
        repo.addWord("word3", false);
        List<String> all = repo.getAllWords();
        assertEquals(3, all.size());
    }

    // -----------------------------------------------------------------------
    // Remove
    // -----------------------------------------------------------------------

    @Test
    public void removeWord_deletesEntry() {
        repo.addWord("toremove", false);
        List<String> before = repo.getAllWords();
        assertEquals(1, before.size());

        // removeWord takes an ID; we need to get the entity ID
        // Since addWord generates a UUID, we use the DAO to find it
        String id = sensitiveWordDao.getAllWords().get(0).id;
        repo.removeWord(id);

        List<String> after = repo.getAllWords();
        assertEquals(0, after.size());
    }

    @Test
    public void emptyRepository_returnsEmptyLists() {
        assertEquals(0, repo.getAllWords().size());
        assertEquals(0, repo.getZeroToleranceWords().size());
    }
}

package com.mine.bookinghelper.data

import androidx.room.*
import com.mine.bookinghelper.model.UserDetail
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDetailDao {
    @Query("SELECT * FROM user_details WHERE id = 1")
    fun getUserDetail(): Flow<UserDetail?>

    @Query("SELECT * FROM user_details WHERE id = 1")
    suspend fun getUserDetailOnce(): UserDetail?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserDetail(userDetail: UserDetail)
}

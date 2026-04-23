package com.mine.bookinghelper.data

import androidx.room.*
import com.mine.bookinghelper.model.GeneralDetails
import com.mine.bookinghelper.model.PersonDetail
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDetailDao {
    @Query("SELECT * FROM general_details WHERE id = 1")
    fun getGeneralDetails(): Flow<GeneralDetails?>

    @Query("SELECT * FROM general_details WHERE id = 1")
    suspend fun getGeneralDetailsOnce(): GeneralDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGeneralDetails(details: GeneralDetails)

    @Query("SELECT * FROM person_details ORDER BY personIndex ASC")
    fun getAllPersons(): Flow<List<PersonDetail>>

    @Query("SELECT * FROM person_details ORDER BY personIndex ASC")
    suspend fun getAllPersonsOnce(): List<PersonDetail>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePerson(person: PersonDetail)

    @Query("DELETE FROM person_details")
    suspend fun deleteAllPersons()
}

package com.example.googlefitapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.SensorRequest
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQ_CODE = 101
    private val ACTIVITY_RECOGNITION_PERMISSION_REQ_CODE = 102
    private val GOOGLE_FIT_PERMISSION_REQ_CODE = 103

    private val TAG = "MYTAG"

    private val fitnessDataResponseModel: FitnessDataResponseModel by lazy {
        FitnessDataResponseModel(
            0f,
            0f,
            0f
        )
    }

    lateinit var tvCalorieCounter: TextView
    lateinit var tvStepsCounter: TextView
    lateinit var tvDistanceCounter: TextView

    lateinit var fitnessOptions: FitnessOptions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvCalorieCounter = findViewById(R.id.tv_calorie_counter)
        tvStepsCounter = findViewById(R.id.tv_steps_counter)
        tvDistanceCounter = findViewById(R.id.tv_distance_counter)

        checkForPermissions()
    }

    private fun checkForPermissions() {
        var isLocationPermissionAllowed = true
        Permission.locationPermissions.forEach {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            )
                isLocationPermissionAllowed = false
        }

        if (!isLocationPermissionAllowed) {
            ActivityCompat.requestPermissions(
                this,
                Permission.locationPermissions,
                LOCATION_PERMISSION_REQ_CODE
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                        ACTIVITY_RECOGNITION_PERMISSION_REQ_CODE
                    )
                } else {
                    checkGoogleFitPermission()
                }
            } else {
                checkGoogleFitPermission()
            }
        }
    }

    private fun checkGoogleFitPermission() {
        fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .build()

        val account = getGoogleAccount()

        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                this,
                GOOGLE_FIT_PERMISSION_REQ_CODE,
                account,
                fitnessOptions
            )
        } else {
            startDataReading()
        }
    }

    private fun startDataReading() {
        getTodayData()

        subscribeAndGetRealTimeData(DataType.TYPE_STEP_COUNT_DELTA)

        requestForHistory()
    }

    private fun subscribeAndGetRealTimeData(dataType: DataType) {
        Fitness.getRecordingClient(this, getGoogleAccount())
            .subscribe(dataType)
            .addOnSuccessListener {
                Log.e(TAG, "Subscribed")
            }
            .addOnFailureListener {
                Log.e(TAG, "Failure : ${it.localizedMessage}")
            }

        getDataUsingSensor(dataType)
    }

    private fun getDataUsingSensor(dataType: DataType) {
        Fitness.getSensorsClient(this, getGoogleAccount())
            .add(
                SensorRequest.Builder()
                    .setDataType(dataType)
                    .setSamplingRate(1, TimeUnit.SECONDS)
                    .build()
            ) {
                val value = it.getValue(Field.FIELD_STEPS).toString().toFloat()
                Log.e(TAG, "Sensor data : $value")
                fitnessDataResponseModel.steps += DecimalFormat("#.##").format(value).toFloat()
                tvStepsCounter.text = fitnessDataResponseModel.steps.toString()
            }
    }

    private fun getTodayData() {
        Fitness.getHistoryClient(this, getGoogleAccount())
            .readDailyTotal(DataType.TYPE_STEP_COUNT_DELTA)
            .addOnSuccessListener { dataset ->
                dataset?.let {
                    getDataFromDataset(it)
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to get step data")
            }

        Fitness.getHistoryClient(this, getGoogleAccount())
            .readDailyTotal(DataType.TYPE_DISTANCE_DELTA)
            .addOnSuccessListener { dataset ->
                dataset?.let {
                    getDataFromDataset(it)
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to get distance data")
            }

        Fitness.getHistoryClient(this, getGoogleAccount())
            .readDailyTotal(DataType.TYPE_CALORIES_EXPENDED)
            .addOnSuccessListener { dataset ->
                dataset?.let {
                    getDataFromDataset(it)
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to get calories data")
            }
    }

    private fun getDataFromDataset(dataset: DataSet) {
        val dataPoints = dataset.dataPoints

        dataPoints.forEach { dataPoint ->
            Log.e(TAG, "data manual : ${dataPoint.originalDataSource.streamName}")

            dataPoint.dataType.fields.forEach { field ->
                val value = dataPoint.getValue(field).toString().toFloat()

                Log.e(TAG, "data : $value")

                when (field.name) {
                    Field.FIELD_STEPS.name -> {
                        fitnessDataResponseModel.steps =
                            DecimalFormat("#.##").format(value).toFloat()
                        tvStepsCounter.text = fitnessDataResponseModel.steps.toString()
                    }
                    Field.FIELD_CALORIES.name -> {
                        fitnessDataResponseModel.calorie =
                            DecimalFormat("#.##").format(value).toFloat()
                        tvCalorieCounter.text = fitnessDataResponseModel.calorie.toString()
                    }
                    Field.FIELD_DISTANCE.name -> {
                        fitnessDataResponseModel.distance =
                            DecimalFormat("#.##").format(value).toFloat()
                        tvDistanceCounter.text = fitnessDataResponseModel.distance.toString()
                    }
                }
            }
        }
    }

    private fun getGoogleAccount(): GoogleSignInAccount {
        return GoogleSignIn.getAccountForExtension(this, fitnessOptions)
    }

    private fun requestForHistory() {
        val cal = Calendar.getInstance()

        cal.time = Date()

        val endTime = cal.timeInMillis

        cal.set(2022, 1, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)

        val startTime = cal.timeInMillis

        val readRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
            .aggregate(DataType.AGGREGATE_STEP_COUNT_DELTA)
            .aggregate(DataType.TYPE_DISTANCE_DELTA)
            .aggregate(DataType.AGGREGATE_DISTANCE_DELTA)
            .aggregate(DataType.TYPE_CALORIES_EXPENDED)
            .aggregate(DataType.AGGREGATE_CALORIES_EXPENDED)
            .bucketByTime(1, TimeUnit.DAYS)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .build()

        Fitness.getHistoryClient(this, getGoogleAccount())
            .readData(readRequest)
            .addOnSuccessListener { dataReadResponse ->
                fitnessDataResponseModel.steps = 0f
                fitnessDataResponseModel.distance = 0f
                fitnessDataResponseModel.calorie = 0f

                if (dataReadResponse.buckets.isNotEmpty()) {
                    val bucketList = dataReadResponse.buckets

                    if (bucketList.isNotEmpty()) {
                        bucketList.forEach { bucket ->
                            val stepsDataSet = bucket.getDataSet(DataType.TYPE_STEP_COUNT_DELTA)
                            stepsDataSet?.let {
                                getDataFromDataReadResponse(it)
                            }

                            val distanceDataSet = bucket.getDataSet(DataType.TYPE_DISTANCE_DELTA)
                            distanceDataSet?.let {
                                getDataFromDataReadResponse(it)
                            }

                            val calorieDataSet = bucket.getDataSet(DataType.TYPE_CALORIES_EXPENDED)
                            calorieDataSet?.let {
                                getDataFromDataReadResponse(it)
                            }
                        }
                    }
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Error occurred!")
            }
    }

    private fun getDataFromDataReadResponse(dataset: DataSet) {
        val dataPoints = dataset.dataPoints

        dataPoints.forEach { dataPoint ->
            dataPoint.dataType.fields.forEach { field ->
                val value = dataPoint.getValue(field).toString().toFloat()

                Log.e(TAG, "data : $value")

                when (field.name) {
                    Field.FIELD_STEPS.name -> {
                        fitnessDataResponseModel.steps =
                            DecimalFormat("#.##").format(value).toFloat()
                        tvStepsCounter.text = fitnessDataResponseModel.steps.toString()
                    }
                    Field.FIELD_CALORIES.name -> {
                        fitnessDataResponseModel.calorie =
                            DecimalFormat("#.##").format(value).toFloat()
                        tvStepsCounter.text = fitnessDataResponseModel.calorie.toString()
                    }
                    Field.FIELD_DISTANCE.name -> {
                        fitnessDataResponseModel.distance =
                            DecimalFormat("#.##").format(value).toFloat()
                        tvStepsCounter.text = fitnessDataResponseModel.distance.toString()
                    }
                }
            }
        }
    }

    private fun unsubscribe(dataType: DataType) {
        Fitness.getRecordingClient(this, getGoogleAccount())
            .unsubscribe(dataType)
            .addOnSuccessListener {
                Log.e(TAG, "Unsubscribed")
            }
            .addOnFailureListener {
                Log.e(TAG, "Failure : ${it.localizedMessage}")
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GOOGLE_FIT_PERMISSION_REQ_CODE) {
            if(resultCode == RESULT_OK) {
                startDataReading()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQ_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACTIVITY_RECOGNITION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                            ACTIVITY_RECOGNITION_PERMISSION_REQ_CODE
                        )
                    } else {
                        checkGoogleFitPermission()
                    }
                } else {
                    checkGoogleFitPermission()
                }
            } else {
                Toast.makeText(this, "Please allow location permissions", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == ACTIVITY_RECOGNITION_PERMISSION_REQ_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkGoogleFitPermission()
            } else {
                Toast.makeText(this, "Please allow activity recognition permissions", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
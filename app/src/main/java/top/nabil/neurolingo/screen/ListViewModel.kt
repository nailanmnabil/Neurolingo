package top.nabil.neurolingo.screen

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neurosky.AlgoSdk.NskAlgoDataType
import com.neurosky.AlgoSdk.NskAlgoSdk
import com.neurosky.AlgoSdk.NskAlgoSdk.NskAlgoInit
import com.neurosky.AlgoSdk.NskAlgoSdk.NskAlgoStart
import com.neurosky.AlgoSdk.NskAlgoSdk.OnAttAlgoIndexListener
import com.neurosky.AlgoSdk.NskAlgoSdk.OnBPAlgoIndexListener
import com.neurosky.AlgoSdk.NskAlgoSdk.OnMedAlgoIndexListener
import com.neurosky.AlgoSdk.NskAlgoSdk.OnSignalQualityListener
import com.neurosky.AlgoSdk.NskAlgoSdk.OnStateChangeListener
import com.neurosky.AlgoSdk.NskAlgoType
import com.neurosky.connection.ConnectionStates
import com.neurosky.connection.DataType.MindDataType
import com.neurosky.connection.EEGPower
import com.neurosky.connection.TgStreamHandler
import com.neurosky.connection.TgStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.nabil.neurolingo.bluetooth.BluetoothController
import top.nabil.neurolingo.bluetooth.BluetoothDeviceDomain
import java.io.OutputStreamWriter

private const val TAG = "tg-callback"

data class BluetoothUiState(
    val pairedBluetooth: List<BluetoothDeviceDomain> = emptyList(),
    val scannedBluetooth: List<BluetoothDeviceDomain> = emptyList(),

    val state: String = "",
    val isScanning: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isDuringSession: Boolean = false,
    val selectedAddress: String = "",
    val timer: Int = 0,
    var panicCounter: Int = 0,
    val attention: Int = 0,
    val meditation: Int = 0,
    val delta: Float = 0f,

    val rawXS: Float = 0f,
    val rawYS: Float = 0f,
    val rawXE: Float = 0f,
    val rawGraph: MutableList<List<Float>> = mutableListOf(),

    val deltaXS: Float = 0f,
    val deltaYS: Float = 0f,
    val deltaXE: Float = 0f,
    val deltaGraph: MutableList<List<Float>> = mutableListOf(),

    val thetaXS: Float = 0f,
    val thetaYS: Float = 0f,
    val thetaXE: Float = 0f,
    val thetaGraph: MutableList<List<Float>> = mutableListOf(),

    val lowAlphaXS: Float = 0f,
    val lowAlphaYS: Float = 0f,
    val lowAlphaXE: Float = 0f,
    val lowAlphaGraph: MutableList<List<Float>> = mutableListOf(),

    val highAlphaXS: Float = 0f,
    val highAlphaYS: Float = 0f,
    val highAlphaXE: Float = 0f,
    val highAlphaGraph: MutableList<List<Float>> = mutableListOf(),

    val lowBetaXS: Float = 0f,
    val lowBetaYS: Float = 0f,
    val lowBetaXE: Float = 0f,
    val lowBetaGraph: MutableList<List<Float>> = mutableListOf(),

    val highBetaXS: Float = 0f,
    val highBetaYS: Float = 0f,
    val highBetaXE: Float = 0f,
    val highBetaGraph: MutableList<List<Float>> = mutableListOf(),

    val lowGammaXS: Float = 0f,
    val lowGammaYS: Float = 0f,
    val lowGammaXE: Float = 0f,
    val lowGammaGraph: MutableList<List<Float>> = mutableListOf(),

    val middleGammaXS: Float = 0f,
    val middleGammaYS: Float = 0f,
    val middleGammaXE: Float = 0f,
    val middleGammaGraph: MutableList<List<Float>> = mutableListOf()
)

sealed class ListScreenEvent {
    data class ShowToast(val message: String) : ListScreenEvent()
}

class ListViewModel(
    private val bluetoothController: BluetoothController,
    private val recordedFilePath: String
) : ViewModel() {

    // TG related var
    private var tgStreamReader: TgStreamReader? = null
    private var badPacketCount = 0
    private val MSG_UPDATE_BAD_PACKET = 1001
    private val MSG_UPDATE_STATE = 1002

    private val _state = MutableStateFlow(BluetoothUiState())
    val state = combine(
        bluetoothController.pairedDevice,
        bluetoothController.scannedDevice,
        bluetoothController.isScanning,
        _state
    ) { pairedDevice, scannedDevice, isScanning, state ->
        state.copy(
            pairedBluetooth = pairedDevice,
            scannedBluetooth = scannedDevice,
            isScanning = isScanning,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    private val _eventFlow = MutableSharedFlow<ListScreenEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    // EEG Algo related var
    private val rawData = ShortArray(512)
    private var rawDataIndex = 0
    private val nskAlgoSdk: NskAlgoSdk = NskAlgoSdk()

    private var saveJob: Job? = null
    private var outputStream: OutputStreamWriter? = null

    private val bpAlgoListener: OnBPAlgoIndexListener =
        OnBPAlgoIndexListener { delta, theta, alpha, beta, gamma ->
            Log.i(
                TAG, "NskAlgoBPAlgoIndexListener: BP: D[" + delta + " dB] T[" + theta + " dB] A[" +
                        alpha + " dB] B[" + beta + " dB] G[" + gamma + "]"
            );
            _state.update { it.copy(delta = delta) }
        }

    private val attAlgoListener: OnAttAlgoIndexListener =
        OnAttAlgoIndexListener { value ->
            Log.i(TAG, "NskAlgoAttAlgoIndexListener: Attention:$value");
        }

    private val meditationAlgoListener: OnMedAlgoIndexListener =
        OnMedAlgoIndexListener { value ->
            Log.i(TAG, "NskAlgoMedAlgoIndexListener: Meditation:$value");
        }

    private val stateChangeAlgoListener: OnStateChangeListener =
        OnStateChangeListener { state, reason ->
            Log.i(TAG, "On State Change: $state [reason:$reason]");
        }

    private val qualityAlgoListener: OnSignalQualityListener =
        OnSignalQualityListener { level ->
            Log.i(TAG, "On Signal Quality: $level");
        }

    init {
        nskAlgoSdk.setOnBPAlgoIndexListener(bpAlgoListener)
        nskAlgoSdk.setOnStateChangeListener(stateChangeAlgoListener)
        nskAlgoSdk.setOnSignalQualityListener(qualityAlgoListener)
        nskAlgoSdk.setOnAttAlgoIndexListener(attAlgoListener)
        nskAlgoSdk.setOnMedAlgoIndexListener(meditationAlgoListener)
        NskAlgoInit(NskAlgoType.NSK_ALGO_TYPE_BP.value, recordedFilePath)
    }

    fun startScan() {
        bluetoothController.startDiscovery()
    }

    fun stopScan() {
        bluetoothController.stopDiscovery()
    }

    fun startRecord(context: Context, backupUri: Uri) {
        _state.update {
            it.copy(
                isDuringSession = true,
                isConnected = false
            )
        }
        val i = NskAlgoStart(false)
        Log.d(TAG, "Berhasil start algo ga $i")

        // Start the background saving job
        startSavingData(context, backupUri)
    }

    fun stopRecord() {
        _state.update {
            it.copy(isDuringSession = false)
        }
        tgStreamReader?.stopRecordRawData()
        tgStreamReader?.stop()
        tgStreamReader?.close()

        // Stop the background saving job
        stopSavingData()
    }
    fun resetRawGraph() {
        _state.update {
            it.copy(
                rawGraph = mutableListOf(),
                rawXS = 0f,
                rawYS = 0f,
                rawXE = 0f
            )
        }
    }

    fun resetDeltaGraph() {
        _state.update {
            it.copy(
                deltaGraph = mutableListOf(),
                deltaXS = 0f,
                deltaYS = 0f,
                deltaXE = 0f
            )
        }
    }

    fun resetThetaGraph() {
        _state.update {
            it.copy(
                thetaGraph = mutableListOf(),
                thetaXS = 0f,
                thetaYS = 0f,
                thetaXE = 0f
            )
        }
    }

    fun resetLowAlphaGraph() {
        _state.update {
            it.copy(
                lowAlphaGraph = mutableListOf(),
                lowAlphaXS = 0f,
                lowAlphaYS = 0f,
                lowAlphaXE = 0f
            )
        }
    }

    fun resetHighAlphaGraph() {
        _state.update {
            it.copy(
                highAlphaGraph = mutableListOf(),
                highAlphaXS = 0f,
                highAlphaYS = 0f,
                highAlphaXE = 0f
            )
        }
    }

    fun resetLowBetaGraph() {
        _state.update {
            it.copy(
                lowBetaGraph = mutableListOf(),
                lowBetaXS = 0f,
                lowBetaYS = 0f,
                lowBetaXE = 0f
            )
        }
    }

    fun resetHighBetaGraph() {
        _state.update {
            it.copy(
                highBetaGraph = mutableListOf(),
                highBetaXS = 0f,
                highBetaYS = 0f,
                highBetaXE = 0f
            )
        }
    }

    fun resetLowGammaGraph() {
        _state.update {
            it.copy(
                lowGammaGraph = mutableListOf(),
                lowGammaXS = 0f,
                lowGammaYS = 0f,
                lowGammaXE = 0f
            )
        }
    }

    fun resetMiddleGammaGraph() {
        _state.update {
            it.copy(
                middleGammaGraph = mutableListOf(),
                middleGammaXS = 0f,
                middleGammaYS = 0f,
                middleGammaXE = 0f
            )
        }
    }

    private fun startSavingData(context: Context, backupUri: Uri) {
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(backupUri)?.let { stream ->
                    val outputStream = OutputStreamWriter(stream).buffered()

                    val csvHeader = listOf(
                        "Timestamp",
                        "Attention",
                        "Meditation",
                        "Delta",
                        "Theta",
                        "Low Alpha",
                        "High Alpha",
                        "Low Beta",
                        "High Beta",
                        "Low Gamma",
                        "Middle Gamma"
                    ).joinToString(",")
                    outputStream.write("$csvHeader\n")

                    while (isActive) {
                        val currentState = _state.value
                        val csvLine = listOf(
                            System.currentTimeMillis(),
                            currentState.attention,
                            currentState.meditation,
                            currentState.delta,
                            currentState.thetaGraph.lastOrNull()?.get(3) ?: 0f,
                            currentState.lowAlphaGraph.lastOrNull()?.get(3) ?: 0f,
                            currentState.highAlphaGraph.lastOrNull()?.get(3) ?: 0f,
                            currentState.lowBetaGraph.lastOrNull()?.get(3) ?: 0f,
                            currentState.highBetaGraph.lastOrNull()?.get(3) ?: 0f,
                            currentState.lowGammaGraph.lastOrNull()?.get(3) ?: 0f,
                            currentState.middleGammaGraph.lastOrNull()?.get(3) ?: 0f
                        ).joinToString(",")

                        outputStream.write("$csvLine\n")
                        outputStream.flush()

                        delay(1000) // Write every second, adjust as needed
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopSavingData() {
        saveJob?.cancel()
        saveJob = null
        outputStream?.close()
        outputStream = null
    }

    fun connect(address: String) {
        bluetoothController.stopDiscovery()

        val bd = bluetoothController.bluetoothAdapter?.getRemoteDevice(address)

        if (tgStreamReader == null) {
            tgStreamReader = TgStreamReader(bd, callback)
            tgStreamReader?.startLog()
            tgStreamReader?.setGetDataTimeOutTime(100)
            tgStreamReader?.setRecordStreamFilePath(recordedFilePath)
        } else {
            tgStreamReader?.changeBluetoothDevice(bd)
            tgStreamReader?.setTgStreamHandler(callback)
            tgStreamReader?.startLog()
            tgStreamReader?.setGetDataTimeOutTime(100)
            tgStreamReader?.setRecordStreamFilePath(recordedFilePath)
        }

        _state.update {
            it.copy(
                isConnecting = true,
                selectedAddress = address,
                state = "menyambungkan..."
            )
        }
        tgStreamReader?.connectAndStart()
    }

    private val callback: TgStreamHandler = object : TgStreamHandler {
        override fun onStatesChanged(connectionStates: Int) {
            Log.d(TAG, "STATE: $connectionStates")

            when (connectionStates) {
                ConnectionStates.STATE_CONNECTING -> {
                    Log.d(TAG, "Connecting")

//                    viewModelScope.launch {
//                        _eventFlow.emit(ListScreenEvent.ShowToast("Connecting"))
//                    }
                }

                ConnectionStates.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected")

                    _state.update {
                        it.copy(state = "tersambung...")
                    }

//                    viewModelScope.launch {
//                        _eventFlow.emit(ListScreenEvent.ShowToast("Connected"))
//                    }
                }

                ConnectionStates.STATE_WORKING -> {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = true,
                            state = "bisa dimulai"
                        )
                    }
                    tgStreamReader?.startRecordRawData()

                    Log.d(TAG, "Working")

//                    viewModelScope.launch {
//                        _eventFlow.emit(ListScreenEvent.ShowToast("Working"))
//                    }
                }

                ConnectionStates.STATE_GET_DATA_TIME_OUT -> {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = false,
                            isDuringSession = false,
                            state = "gagal tersambung"
                        )
                    }

                    stopRecord()

                    Log.d(TAG, "Stopped")

//                    viewModelScope.launch {
//                        _eventFlow.emit(ListScreenEvent.ShowToast("Stopped"))
//                    }
                }

                ConnectionStates.STATE_FAILED -> {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isDuringSession = false,
                            isConnected = false,
                            state = "gagal tersambung"
                        )
                    }

                    stopRecord()
                }
            }

            val msg: Message = linkDetectedHandler.obtainMessage()
            msg.what = MSG_UPDATE_STATE
            msg.arg1 = connectionStates
            linkDetectedHandler.sendMessage(msg)
        }

        override fun onRecordFail(a: Int) {
            Log.e(TAG, "onRecordFail: $a")
        }

        override fun onChecksumFail(payload: ByteArray, length: Int, checksum: Int) {
            Log.e(TAG, "onChecksumFail: $length")

            badPacketCount++
            val msg: Message = linkDetectedHandler.obtainMessage()
            msg.what = MSG_UPDATE_BAD_PACKET
            msg.arg1 = badPacketCount
            linkDetectedHandler.sendMessage(msg)
        }

        override fun onDataReceived(datatype: Int, data: Int, obj: Any?) {
            Log.e(TAG, "onDataReceived: $data")

            val msg = linkDetectedHandler.obtainMessage()
            msg.what = datatype
            msg.arg1 = data
            msg.obj = obj
            linkDetectedHandler.sendMessage(msg)
        }
    }

    @SuppressLint("HandlerLeak")
    private val linkDetectedHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MindDataType.CODE_RAW -> {
                    Log.d(TAG, "CODE_RAW " + msg.arg1)

                    rawData[rawDataIndex] = msg.arg1.toShort()
                    rawDataIndex += 1

                    if (rawDataIndex == 512) {
                        for (v in rawData) {
                            println("bosku $v")
                        }
                        NskAlgoSdk.NskAlgoDataStream(
                            NskAlgoDataType.NSK_ALGO_DATA_TYPE_EEG.value,
                            rawData,
                            rawDataIndex
                        )
                        rawDataIndex = 0
                    }

                    _state.value.rawGraph.add(
                        listOf(
                            _state.value.rawXS,
                            _state.value.rawYS,
                            _state.value.rawXE,
                            msg.arg1.toFloat()
                        )
                    )
                    _state.update {
                        it.copy(
                            rawXS = it.rawXE,
                            rawYS = msg.arg1.toFloat(),
                            rawXE = it.rawXS + 1.dp.value,
                        )
                    }

                }

                MindDataType.CODE_MEDITATION -> {
                    Log.d(TAG, "CODE_MEDITATION " + msg.arg1)
                    _state.update {
                        it.copy(meditation = msg.arg1)
                    }
                }

                MindDataType.CODE_ATTENTION -> {
                    Log.d(TAG, "CODE_ATTENTION " + msg.arg1)
                    _state.update {
                        it.copy(
                            attention = msg.arg1,
                            timer = _state.value.timer + 1
                        )
                    }

                    if (_state.value.attention > 80 && _state.value.meditation < 20) {
                        _state.update {
                            it.copy(panicCounter = _state.value.panicCounter++)
                        }
                    }
                }

                MindDataType.CODE_POOR_SIGNAL -> {
                    val poorSignal = msg.arg1
                    Log.d(TAG, "CODE_POOR_SIGNAL $poorSignal")
                }

                MindDataType.CODE_EEGPOWER -> {
                    val power: EEGPower = msg.obj as EEGPower
                    Log.d(TAG, "MSG_UPDATE_BAD_PACKET " + msg.arg1)

                    if (power.isValidate) {
                        Log.d(TAG, "POWER.DELTA " + power.delta)
                        Log.d(TAG, "POWER.THETA " + power.theta)
                        Log.d(TAG, "POWER.LOW_ALPHA " + power.lowAlpha)
                        Log.d(TAG, "POWER.HIGH_ALPHA " + power.highAlpha)
                        Log.d(TAG, "POWER.LOW_BETA " + power.lowBeta)
                        Log.d(TAG, "POWER.HIGH_BETA " + power.highBeta)
                        Log.d(TAG, "POWER.LOW_GAMMA " + power.lowGamma)
                        Log.d(TAG, "POWER.MIDDLE_GAMMA " + power.middleGamma)

                        _state.value.deltaGraph.add(
                            listOf(
                                _state.value.deltaXS,
                                _state.value.deltaYS,
                                _state.value.deltaXE,
                                power.delta.toFloat()
                            )
                        )

                        _state.update {
                            it.copy(
                                deltaXS = it.deltaXE,
                                deltaYS = power.delta.toFloat(),
                                deltaXE = it.deltaXS + 20.dp.value
                            )
                        }

                        _state.value.thetaGraph.add(
                            listOf(
                                _state.value.thetaXS,
                                _state.value.thetaYS,
                                _state.value.thetaXE,
                                power.theta.toFloat()
                            )
                        )

                        _state.update {
                            it.copy(
                                thetaXS = it.thetaXE,
                                thetaYS = power.theta.toFloat(),
                                thetaXE = it.thetaXS + 20.dp.value
                            )
                        }

                        _state.value.lowAlphaGraph.add(
                            listOf(
                                _state.value.lowAlphaXS,
                                _state.value.lowAlphaYS,
                                _state.value.lowAlphaXE,
                                power.lowAlpha.toFloat()
                            )
                        )

                        _state.update {
                            it.copy(
                                lowAlphaXS = it.lowAlphaXE,
                                lowAlphaYS = power.lowAlpha.toFloat(),
                                lowAlphaXE = it.lowAlphaXS + 20.dp.value
                            )
                        }

                        _state.value.highAlphaGraph.add(
                            listOf(
                                _state.value.highAlphaXS,
                                _state.value.highAlphaYS,
                                _state.value.highAlphaXE,
                                power.highAlpha.toFloat()
                            )
                        )

                        _state.update {
                            it.copy(
                                highAlphaXS = it.highAlphaXE,
                                highAlphaYS = power.highAlpha.toFloat(),
                                highAlphaXE = it.highAlphaXS + 20.dp.value
                            )
                        }

                        _state.value.lowBetaGraph.add(
                            listOf(
                                _state.value.lowBetaXS,
                                _state.value.lowBetaYS,
                                _state.value.lowBetaXE,
                                power.lowBeta.toFloat()
                            )
                        )

                        _state.update {
                            it.copy(
                                lowBetaXS = it.lowBetaXE,
                                lowBetaYS = power.lowBeta.toFloat(),
                                lowBetaXE = it.lowBetaXS + 20.dp.value
                            )
                        }

                        _state.value.highBetaGraph.add(
                            listOf(
                                _state.value.highBetaXS,
                                _state.value.highBetaYS,
                                _state.value.highBetaXE,
                                power.highBeta.toFloat()
                            )
                        )

                        _state.update {
                            it.copy(
                                highBetaXS = it.highBetaXE,
                                highBetaYS = power.highBeta.toFloat(),
                                highBetaXE = it.highBetaXS + 20.dp.value
                            )
                        }

                        _state.value.lowGammaGraph.add(
                            listOf(
                                _state.value.lowGammaXS,
                                _state.value.lowGammaYS,
                                _state.value.lowGammaXE,
                                power.lowGamma.toFloat()
                            )
                        )

                        _state.update {
                            it.copy(
                                lowGammaXS = it.lowGammaXE,
                                lowGammaYS = power.lowGamma.toFloat(),
                                lowGammaXE = it.lowGammaXS + 20.dp.value
                            )
                        }

                        _state.value.middleGammaGraph.add(
                            listOf(
                                _state.value.middleGammaXS,
                                _state.value.middleGammaYS,
                                _state.value.middleGammaXE,
                                power.middleGamma.toFloat()
                            )
                        )

                        _state.update {
                            it.copy(
                                middleGammaXS = it.middleGammaXE,
                                middleGammaYS = power.middleGamma.toFloat(),
                                middleGammaXE = it.middleGammaXS + 20.dp.value
                            )
                        }
                    }
                }

                MSG_UPDATE_BAD_PACKET -> {
                    Log.d(TAG, "MSG_UPDATE_BAD_PACKET ${msg.arg1}")
                }

                else -> {}
            }
            super.handleMessage(msg)
        }
    }
}

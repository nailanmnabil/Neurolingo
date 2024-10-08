package top.nabil.neurolingo.screen

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.nabil.neurolingo.R
import top.nabil.neurolingo.ui.theme.Kanit

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    vm: ListViewModel,
    context: Context
) {
    val state = vm.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(key1 = true) {
        vm.eventFlow.collect { event ->
            when (event) {
                is ListScreenEvent.ShowToast -> {
                    coroutineScope.launch {
                        Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            vm.startRecord(context, uri)
        } else {
            coroutineScope.launch {
                Toast.makeText(context, "Mohon pilih lokasi penyimpanan data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier
                    .background(Color(0xFFD9D9D9)),
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = null
                    )
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues)
                .padding(
                    top = 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
        ) {
            if (state.value.isDuringSession) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {

                    Text(text = state.value.delta.toString())

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Raw data",
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Text(
                            text = state.value.rawYS.toString(),
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Canvas(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        for (l in state.value.rawGraph) {
                            var yS = (l[1] - 1) / 1999 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)
                            var yE = (l[3] - 1) / 1999 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)

                            if (yE > size.height) {
                                yE = size.height
                            }
                            if (yS > size.height) {
                                yS = size.height
                            }

                            drawLine(
                                color = Color.Green,
                                start = Offset(l[0], yS),
                                end = Offset(l[2], yE),
                                strokeWidth = 1.dp.toPx(),
                            )
                            if (l[2] > size.width) {
                                vm.resetRawGraph()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Delta",
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Text(
                            text = state.value.deltaYS.toString(),
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Canvas(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        for (l in state.value.deltaGraph) {
                            var yS = (l[1] - 1) / 1000000 * (300.dp.toPx() + 1)
                            var yE = (l[3] - 1) / 1000000 * (300.dp.toPx() + 1)

                            if (yE > size.height) {
                                yE = size.height
                            }
                            if (yS > size.height) {
                                yS = size.height
                            }

                            drawLine(
                                color = Color.Green,
                                start = Offset(l[0], yS),
                                end = Offset(l[2], yE),
                                strokeWidth = 2.dp.toPx(),
                            )
                            if (l[2] > size.width) {
                                vm.resetDeltaGraph()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Theta",
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Text(
                            text = state.value.thetaYS.toString(),
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Canvas(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        for (l in state.value.thetaGraph) {
                            var yS =
                                (l[1] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)
                            var yE =
                                (l[3] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)

                            if (yE > size.height) {
                                yE = size.height
                            }
                            if (yS > size.height) {
                                yS = size.height
                            }

                            drawLine(
                                color = Color.Green,
                                start = Offset(l[0], yS),
                                end = Offset(l[2], yE),
                                strokeWidth = 2.dp.toPx(),
                            )
                            if (l[2] > size.width) {
                                vm.resetThetaGraph()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Low Alpha",
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Text(
                            text = state.value.lowAlphaYS.toString(),
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Canvas(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        for (l in state.value.lowAlphaGraph) {
                            var yS =
                                (l[1] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)
                            var yE =
                                (l[3] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)

                            if (yE > size.height) {
                                yE = size.height
                            }
                            if (yS > size.height) {
                                yS = size.height
                            }

                            drawLine(
                                color = Color.Green,
                                start = Offset(l[0], yS),
                                end = Offset(l[2], yE),
                                strokeWidth = 2.dp.toPx(),
                            )
                            if (l[2] > size.width) {
                                vm.resetLowAlphaGraph()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "High Alpha",
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Text(
                            text = state.value.highAlphaYS.toString(),
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }


                    Spacer(Modifier.height(8.dp))

                    Canvas(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        for (l in state.value.highAlphaGraph) {
                            var yS =
                                (l[1] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)
                            var yE =
                                (l[3] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)

                            if (yE > size.height) {
                                yE = size.height
                            }
                            if (yS > size.height) {
                                yS = size.height
                            }

                            drawLine(
                                color = Color.Green,
                                start = Offset(l[0], yS),
                                end = Offset(l[2], yE),
                                strokeWidth = 2.dp.toPx(),
                            )
                            if (l[2] > size.width) {
                                vm.resetHighAlphaGraph()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Low Beta",
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Text(
                            text = state.value.lowBetaYS.toString(),
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Canvas(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        for (l in state.value.lowBetaGraph) {
                            var yS =
                                (l[1] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)
                            var yE =
                                (l[3] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)

                            if (yE > size.height) {
                                yE = size.height
                            }
                            if (yS > size.height) {
                                yS = size.height
                            }

                            drawLine(
                                color = Color.Green,
                                start = Offset(l[0], yS),
                                end = Offset(l[2], yE),
                                strokeWidth = 2.dp.toPx(),
                            )
                            if (l[2] > size.width) {
                                vm.resetLowBetaGraph()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "High Beta",
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Text(
                            text = state.value.highBetaYS.toString(),
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Canvas(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        for (l in state.value.highBetaGraph) {
                            var yS =
                                (l[1] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)
                            var yE =
                                (l[3] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)

                            if (yE > size.height) {
                                yE = size.height
                            }
                            if (yS > size.height) {
                                yS = size.height
                            }

                            drawLine(
                                color = Color.Green,
                                start = Offset(l[0], yS),
                                end = Offset(l[2], yE),
                                strokeWidth = 2.dp.toPx(),
                            )
                            if (l[2] > size.width) {
                                vm.resetHighBetaGraph()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Low Gamma",
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Text(
                            text = state.value.lowGammaYS.toString(),
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Canvas(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        for (l in state.value.lowGammaGraph) {
                            var yS =
                                (l[1] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)
                            var yE =
                                (l[3] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)

                            if (yE > size.height) {
                                yE = size.height
                            }
                            if (yS > size.height) {
                                yS = size.height
                            }

                            drawLine(
                                color = Color.Green,
                                start = Offset(l[0], yS),
                                end = Offset(l[2], yE),
                                strokeWidth = 2.dp.toPx(),
                            )
                            if (l[2] > size.width) {
                                vm.resetLowGammaGraph()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Middle Gamma",
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Text(
                            text = state.value.middleGammaYS.toString(),
                            fontFamily = Kanit,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Canvas(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        for (l in state.value.middleGammaGraph) {
                            var yS =
                                (l[1] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)
                            var yE =
                                (l[3] - 1) / 1000000 * (300.dp.toPx() + 1) + (300.dp.toPx() / 2)

                            if (yE > size.height) {
                                yE = size.height
                            }
                            if (yS > size.height) {
                                yS = size.height
                            }

                            drawLine(
                                color = Color.Green,
                                start = Offset(l[0], yS),
                                end = Offset(l[2], yE),
                                strokeWidth = 2.dp.toPx(),
                            )
                            if (l[2] > size.width) {
                                vm.resetMiddleGammaGraph()
                            }
                        }
                    }

                    Text(text = (state.value.timer).toString())
                    Text(text = (state.value.attention).toString())
                    Text(text = (state.value.panicCounter).toString())
                    Text(text = (state.value.meditation).toString())
                    Text(text = (if (state.value.timer == 0) 0 else state.value.panicCounter / state.value.timer).toString())
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    if (state.value.scannedBluetooth.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Tidak ada perangkat",
                                    fontFamily = Kanit,
                                    fontSize = 16.sp,
                                    color = Color(0xFF787878)
                                )
                            }
                        }
                    }

                    items(
                        items = state.value.scannedBluetooth,
                        key = { "${it.address}${(1..100).random()}" }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (state.value.isConnected && it.address == state.value.selectedAddress) Color(
                                        0xFF69BD6C
                                    )
                                    else Color(
                                        0xFFF0F0F0
                                    )
                                )
                                .clickable {
                                    if (state.value.isConnecting) {
                                        vm.stopRecord()
                                    } else {
                                        vm.connect(it.address)
                                    }
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    modifier = Modifier
                                        .padding(bottom = 2.dp),
                                    text = it.name,
                                    fontFamily = Kanit,
                                    fontSize = 20.sp,
                                    color =
                                    if (state.value.isConnected && it.address == state.value.selectedAddress) Color.White
                                    else Color.Black
                                )
                                Text(
                                    modifier = Modifier
                                        .padding(bottom = 2.dp),
                                    text = it.address,
                                    fontFamily = Kanit,
                                    fontSize = 16.sp,
                                    color =
                                    if (state.value.isConnected && it.address == state.value.selectedAddress) Color(
                                        0xFFE6E6E6
                                    )
                                    else Color(0xFF999999)
                                )
                                if (state.value.isConnecting && it.address == state.value.selectedAddress) {
                                    Text(
                                        modifier = Modifier
                                            .padding(bottom = 2.dp),
                                        text = state.value.state,
                                        fontFamily = Kanit,
                                        fontSize = 12.sp,
                                        color = if (state.value.state != "bisa dimulai") Color(
                                            0xFFDA9D00
                                        ) else Color(
                                            0xFF3A9D3E
                                        )
                                    )
                                }
                            }
                            if (state.value.isConnecting && it.address == state.value.selectedAddress) {
                                CircularProgressIndicator(
                                    strokeWidth = 3.dp,
                                    color = Color(0xFFDA9D00),
                                    modifier = Modifier
                                        .size(30.dp)
                                )
                            }
                        }
                    }
                }
            }

            Button(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.value.isConnected) {
                        Color(0xFF3A9D3E)
                    } else if (state.value.isDuringSession) {
                        Color(0xFFB60D0D)
                    } else {
                        Color(0xFF27477D)
                    }
                ),
                onClick = {
                    if (state.value.isScanning) {
                        vm.stopScan()
                    } else if (state.value.isDuringSession) {
                        vm.stopRecord()
                    } else if (state.value.isConnected) {
                        launcher.launch("eeg_recording_${System.currentTimeMillis()}.csv")
                    } else {
                        vm.startScan()
                    }
                },
            ) {
                Text(
                    text = if (state.value.isScanning) "Stop scan"
                    else if (state.value.isConnected) "Mulai sesi"
                    else if (state.value.isDuringSession) "Stop sesi"
                    else "Mulai scan",
                    fontFamily = Kanit,
                    fontSize = 24.sp,
                    color = Color.White
                )
                if (state.value.isScanning) {
                    Spacer(modifier = Modifier.width(12.dp))
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp,
                        color = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                    )
                }
            }
        }
    }
}

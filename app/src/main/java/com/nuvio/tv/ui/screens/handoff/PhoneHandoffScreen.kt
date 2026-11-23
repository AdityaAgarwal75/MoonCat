package com.nuvio.tv.ui.screens.handoff

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.handoff.PhoneHandoffReceiver
import com.nuvio.tv.core.qr.QrCodeGenerator

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PhoneHandoffScreen(receiver: PhoneHandoffReceiver) {
    val status by receiver.status.collectAsStateWithLifecycle()
    val qrPayload by receiver.pairingQr.collectAsStateWithLifecycle()
    val qr = remember(qrPayload) { qrPayload?.let { QrCodeGenerator.generate(it).asImageBitmap() } }
    val actionFocus = remember { FocusRequester() }
    var networkUnavailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        actionFocus.requestFocus()
    }
    DisposableEffect(receiver) {
        onDispose { receiver.closePairing() }
    }

    Row(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(42.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.widthIn(max = 430.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.phone_handoff_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.phone_handoff_instructions), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    when {
                        networkUnavailable || status.enabled && !status.online -> R.string.phone_handoff_offline
                        qr != null -> R.string.phone_handoff_pending
                        status.paired -> R.string.phone_handoff_ready
                        else -> R.string.phone_handoff_inactive
                    }
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { networkUnavailable = !receiver.beginPairing() },
                    modifier = Modifier.focusRequester(actionFocus)
                ) {
                    Text(stringResource(R.string.phone_handoff_show_code))
                }
                if (status.enabled) {
                    Button(onClick = {
                        receiver.revoke()
                        networkUnavailable = false
                        actionFocus.requestFocus()
                    }) {
                        Text(stringResource(R.string.phone_handoff_disconnect))
                    }
                }
            }
        }
        if (qr != null) {
            Image(
                bitmap = qr,
                contentDescription = stringResource(R.string.phone_handoff_show_code),
                modifier = Modifier.size(205.dp)
            )
        }
    }
}

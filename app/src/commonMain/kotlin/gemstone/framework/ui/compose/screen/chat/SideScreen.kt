package gemstone.framework.ui.compose.screen.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gemstone.framework.ui.compose.theme.*
import gemstone.app.generated.resources.*
import gemstone.app.generated.resources.Res
import gemstone.app.generated.resources.bell
import gemstone.app.generated.resources.search
import gemstone.app.generated.resources.sliders
import gemstone.framework.ui.viewmodel.AIModelViewModel
import gemstone.framework.ui.viewmodel.AIModelViewModel.InputKind
import gemstone.framework.ui.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonColors
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties


@Composable
fun SideScreen(
    sideBarMode: Boolean = true,
    onChatSelected: (Int) -> Unit = { _ -> }
) {
    val modifier = when (sideBarMode) {
        false -> Modifier.fillMaxSize()
        true -> Modifier.fillMaxHeight().width(Dimen.SIDEBAR_WIDTH)
    }

    Column(modifier = modifier) {
        Spacer(modifier = Modifier.fillMaxWidth().padding(top = Dimen.LAYOUT_PADDING))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimen.LAYOUT_PADDING),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TitleText(stringResource(Res.string.app_title), letterSpacing = (-1).sp, fontSize = 25.sp)
            Spacer(modifier = Modifier.width(Dimen.LIST_ELEMENT_SPACING))
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BlurredFluxIconButton(
                    onClick = {},
                    iconResource = IconResource.Drawable(Res.drawable.bell),
                    iconDescription = stringResource(Res.string.sidebar_notification_section),
                    modifier = Modifier.size(Dimen.BIG_BUTTON_SIZE),
                    shape = MaterialTheme.shapes.large.copy(Dimen.BIG_BUTTON_CORNER_RADIUS),
                    contentPadding = PaddingValues(Dimen.BIG_BUTTON_PADDING)
                )
                Spacer(modifier = Modifier.width(Dimen.LIST_ELEMENT_SPACING))
                PrimaryFluxButton(
                    onClick = { /* TODO: Handle new chat */ },
                    modifier = Modifier.size(Dimen.BIG_BUTTON_SIZE),
                    shape = MaterialTheme.shapes.large.copy(Dimen.BIG_BUTTON_CORNER_RADIUS)
                ) {
                    SubtitleText(SettingsViewModel.userInitial, fontWeight = FontWeight.ExtraLight, maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimen.LAYOUT_PADDING))

//        SettingTitleBar(
//            title = stringResource(Res.string.sidebar_models_section),
//            iconResource = Res.drawable.sliders,
//            iconDescription = stringResource(Res.string.sidebar_models_section_desc),
//            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimen.LAYOUT_PADDING)
//        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimen.LAYOUT_PADDING),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubtitleText(stringResource(Res.string.sidebar_models_section))
            SecondaryFluxIconButton(
                onClick = { AIModelViewModel.isEditingServerHost = !AIModelViewModel.isEditingServerHost },
                iconResource = IconResource.Drawable(Res.drawable.sliders),
                iconDescription = stringResource(Res.string.sidebar_models_section_desc),
                elevation = ButtonDefaults.elevatedButtonElevation(0.4.dp),
                shape = MaterialTheme.shapes.medium.copy(CornerSize(14.dp)),
                modifier = Modifier,
            )
        }

        if (AIModelViewModel.isEditingServerHost) {
            ServerModelPopup(
                onDismiss = { AIModelViewModel.isEditingServerHost = false }
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Dimen.LAYOUT_PADDING),
            horizontalArrangement = Arrangement.spacedBy(Dimen.LIST_ELEMENT_SPACING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val select = { modelInfo: Pair<String, String> ->
                if (modelInfo.first == "All") {
                    AIModelViewModel.deselectAIModel()
                } else {
                    AIModelViewModel.selectAIModel(modelInfo.first, modelInfo.second)
                }
            }
            for (modelInfo in listOf(Pair("All", "Using Default Model")) + AIModelViewModel.availableAIModels) {
                item(modelInfo) {
                    if (modelInfo.first == AIModelViewModel.selectedAIModel || (AIModelViewModel.selectedAIModel.isEmpty() && modelInfo.first == "All")) {
                        PrimaryFluxButton(
                            onClick = { select(modelInfo) },
                            shape = MaterialTheme.shapes.large.copy(Dimen.BIG_BUTTON_CORNER_RADIUS),
                            contentPadding = PaddingValues(Dimen.BIG_BUTTON_PADDING)
                        ) {
                            BodyText(modelInfo.first)
                        }
                    } else {
                        BlurredFluxButton(
                            onClick = { select(modelInfo) },
                            shape = MaterialTheme.shapes.large.copy(Dimen.BIG_BUTTON_CORNER_RADIUS),
                            contentPadding = PaddingValues(Dimen.BIG_BUTTON_PADDING)
                        ) {
                            BodyText(modelInfo.first)
                        }
                    }
                }
            }
        }

        val titlePlacement = mapOf(-2 to Pair(false, stringResource(Res.string.sidebar_recent_chats_section)))
        val newChatPlacement = mapOf(-1 to Pair(false, stringResource(Res.string.chat_title_placeholder)))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimen.LAYOUT_PADDING),
            verticalArrangement = Arrangement.spacedBy(Dimen.LIST_ELEMENT_SPACING),
            reverseLayout = !sideBarMode
        ) {
            val list = when (sideBarMode) {
                true -> titlePlacement + newChatPlacement + AIModelViewModel.chatRoomList
                false -> newChatPlacement + AIModelViewModel.chatRoomList + titlePlacement
            }
            list.forEach { (key, value) ->
                item(key) {
                    if (key == -2) {
//                        SettingTitleBar(
//                            title = value.second,
//                            iconResource = Res.drawable.search,
//                            iconDescription = stringResource(Res.string.sidebar_recent_chats_section_desc),
//                            modifier = Modifier.fillMaxWidth()
//                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SubtitleText(value.second)
                            SecondaryFluxIconButton(
                                onClick = {},
                                iconResource = IconResource.Drawable(Res.drawable.search),
                                iconDescription = stringResource(Res.string.sidebar_recent_chats_section_desc),
                                elevation = ButtonDefaults.elevatedButtonElevation(0.4.dp),
                                shape = MaterialTheme.shapes.medium.copy(CornerSize(14.dp)),
                                modifier = Modifier,
                            )
                        }
                    } else {
                        BlurredFluxButton(
                            onClick = { onChatSelected(key) },
                            shape = MaterialTheme.shapes.large.copy(Dimen.SURFACE_CORNER_RADIUS),
                            clickAnimation = Dimen.SURFACE_CLICK_ANIMATION,
                            elevation = Dimen.SURFACE_ELEVATIONS,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SecondaryFluxIconButton(
                                    onClick = { onChatSelected(key) },
                                    iconResource = IconResource.Drawable(Res.drawable.arrow_up_right),
                                    iconDescription = "Open This Chat",
                                    modifier = Modifier,
                                    shape = MaterialTheme.shapes.extraLarge,
                                    elevation = ButtonDefaults.elevatedButtonElevation(0.dp)
                                )
                                SubtitleText(value.second, fontSize = 16.sp, fontWeight = FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerModelPopup(onDismiss: () -> Unit) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)).clickable { onDismiss() }
            )

            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimen.LAYOUT_PADDING).align(Alignment.Center)
            ) {
                var newHost by remember { mutableStateOf(AIModelViewModel.serverHost) }
                var apiMode by remember { mutableStateOf(false) } // false: SERVER, true: API KEY
                var apiKey by remember { mutableStateOf("") }

                BlurredFluxButton(
                    onClick = {},
                    shape = MaterialTheme.shapes.large.copy(Dimen.BIG_BUTTON_CORNER_RADIUS),
                    elevation = ButtonDefaults.buttonElevation(1.dp),
                    colors = ButtonColors(
                        containerColor = Color(0xFFFBFBFB),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = Color(0xFFF9F9F9),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    ),
                    contentPadding = PaddingValues(Dimen.LAYOUT_PADDING)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Dimen.LIST_ELEMENT_SPACING)
                    ) {
                        TitleText("서버/API 설정", fontSize = 20.sp)

                        // Toggle + input row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val chipPad = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            // SERVER chip
                            if (!apiMode) {
                                PrimaryFluxButton(
                                    onClick = { apiMode = false },
                                    shape = MaterialTheme.shapes.extraLarge,
                                    contentPadding = chipPad
                                ) { BodyText("SERVER", fontSize = 12.sp) }
                            } else {
                                BlurredFluxButton(
                                    onClick = { apiMode = false },
                                    shape = MaterialTheme.shapes.extraLarge,
                                    contentPadding = chipPad
                                ) { CaptionText("SERVER", fontSize = 12.sp) }
                            }
                            // API KEY chip
                            if (apiMode) {
                                PrimaryFluxButton(
                                    onClick = { apiMode = true },
                                    shape = MaterialTheme.shapes.extraLarge,
                                    contentPadding = chipPad
                                ) { BodyText("API KEY", fontSize = 12.sp) }
                            } else {
                                BlurredFluxButton(
                                    onClick = { apiMode = true },
                                    shape = MaterialTheme.shapes.extraLarge,
                                    contentPadding = chipPad
                                ) { CaptionText("API KEY", fontSize = 12.sp) }
                            }

                            // Input field
                            SecondaryFluxButton(
                                onClick = {},
                                modifier = Modifier.weight(1f),
                                elevation = ButtonDefaults.buttonElevation(0.4.dp),
                                clickAnimation = Dimen.SURFACE_CLICK_ANIMATION,
                                hoverAnimation = null,
                                interactionSource = remember { NoRippleInteractionSource() },
                                enabled = false,
                                shape = MaterialTheme.shapes.extraLarge,
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                if (!apiMode) {
                                    BasicTextField(
                                        value = newHost,
                                        onValueChange = { newHost = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 6.dp)
                                            .onKeyEvent {
                                                if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
                                                    if (newHost.isNotBlank()) AIModelViewModel.changeServerHost(newHost)
                                                    onDismiss()
                                                    true
                                                } else false
                                            },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            fontFamily = SuiteFontFamily
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        decorationBox = { inner ->
                                            if (newHost.isEmpty()) {
                                                Text(
                                                    text = "Enter server address",
                                                    style = TextStyle(color = Color.Gray),
                                                    fontFamily = SuiteFontFamily
                                                )
                                            }
                                            inner()
                                        }
                                    )
                                } else {
                                    BasicTextField(
                                        value = apiKey,
                                        onValueChange = { apiKey = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 6.dp)
                                            .onKeyEvent {
                                                if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
                                                    if (apiKey.isNotBlank()) AIModelViewModel.addRecentApiKey(apiKey)
                                                    onDismiss()
                                                    true
                                                } else false
                                            },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            fontFamily = SuiteFontFamily
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        decorationBox = { inner ->
                                            if (apiKey.isEmpty()) {
                                                Text(
                                                    text = "Enter API key",
                                                    style = TextStyle(color = Color.Gray),
                                                    fontFamily = SuiteFontFamily
                                                )
                                            }
                                            inner()
                                        }
                                    )
                                }
                            }
                        }

                        // Recent inputs (SERVER and API KEY mixed)
                        Column {
                            CaptionText("최근 입력", color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for ((kind, value) in AIModelViewModel.recentInputs) {
                                    val title = when (kind) {
                                        InputKind.SERVER -> "Server"
                                        InputKind.API_KEY -> "API KEY"
                                    }
                                    val subtitle = when (kind) {
                                        InputKind.SERVER -> value
                                        InputKind.API_KEY -> value
                                    }
                                    BlurredFluxCard(
                                        onClick = {
                                            if (kind == InputKind.SERVER) {
                                                newHost = value
                                                apiMode = false
                                            } else {
                                                apiKey = value
                                                apiMode = true
                                            }
                                        },
                                        modifier = Modifier.padding(Dimen.LAYOUT_PADDING / 2).size(140.dp),
                                        iconModifier = Modifier.size(28.dp),
                                        iconResource = IconResource.Drawable(Res.drawable.sliders),
                                        iconDescription = title,
                                        hoverAnimation = HoverAnimation(0f, -20f),
                                        shape = MaterialTheme.shapes.large.copy(Dimen.BIG_BUTTON_CORNER_RADIUS),
                                        contentPadding = PaddingValues(Dimen.BIG_BUTTON_PADDING),
                                        elevation = ButtonDefaults.buttonElevation(1.dp),
                                        colors = ButtonColors(
                                            containerColor = Color(0xFFFBFBFB),
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            disabledContainerColor = Color(0xFFF9F9F9),
                                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        BodyText(title, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        CaptionText(subtitle, letterSpacing = (-1).sp, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SecondaryFluxButton(
                                onClick = onDismiss,
                                shape = MaterialTheme.shapes.large.copy(Dimen.BIG_BUTTON_CORNER_RADIUS),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                BodyText("취소")
                            }
                            Spacer(modifier = Modifier.width(Dimen.LIST_ELEMENT_SPACING))
                            PrimaryFluxIconButton(
                                onClick = {
                                    if (!apiMode) {
                                        if (newHost.isNotBlank()) AIModelViewModel.changeServerHost(newHost)
                                    } else {
                                        if (apiKey.isNotBlank()) AIModelViewModel.addRecentApiKey(apiKey)
                                    }
                                    onDismiss()
                                },
                                iconResource = IconResource.Drawable(Res.drawable.arrow_up),
                                iconDescription = "Save",
                                shape = MaterialTheme.shapes.extraLarge,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

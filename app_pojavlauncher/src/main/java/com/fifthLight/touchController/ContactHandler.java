package com.fifthLight.touchController;

import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

import top.fifthlight.touchcontroller.proxy.client.LauncherSocketProxyClient;
import top.fifthlight.touchcontroller.proxy.data.Offset;
import top.fifthlight.touchcontroller.proxy.message.AddPointerMessage;
import top.fifthlight.touchcontroller.proxy.message.ClearPointerMessage;
import top.fifthlight.touchcontroller.proxy.message.RemovePointerMessage;

public class ContactHandler {

    private static final SparseIntArray pointerIdMap = new SparseIntArray();
    private static int nextPointerId = 1;

    public void progressEvent(MotionEvent motionEvent, View touchView) {
        LauncherSocketProxyClient proxy = ControllerProxy.getProxyClient();
        if (proxy != null) {
            switch (motionEvent.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    int pointerId = nextPointerId++;
                    pointerIdMap.put(motionEvent.getPointerId(0), pointerId);
                    proxy.trySend(new AddPointerMessage(pointerId, new Offset(
                            motionEvent.getX(0) / touchView.getWidth(),
                            motionEvent.getY(0) / touchView.getHeight()
                    )));
                    break;
                }
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int pointerId = nextPointerId++;
                    int i = motionEvent.getActionIndex();
                    pointerIdMap.put(motionEvent.getPointerId(i), pointerId);
                    proxy.trySend(new AddPointerMessage(pointerId, new Offset(
                            motionEvent.getX(i) / touchView.getWidth(),
                            motionEvent.getY(i) / touchView.getHeight()
                    )));
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    for (int i = 0; i < motionEvent.getPointerCount(); i++) {
                        int pointerId = pointerIdMap.get(motionEvent.getPointerId(i));
                        if (pointerId == 0) {
                            // Log.d("InGameEventProcessor", "Move pointerId is 0");
                        }
                        proxy.trySend(new AddPointerMessage(pointerId, new Offset(
                                motionEvent.getX(i) / touchView.getWidth(),
                                motionEvent.getY(i) / touchView.getHeight()
                        )));
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    clearPointer(proxy);
                    break;
                case MotionEvent.ACTION_POINTER_UP: {
                    int i = motionEvent.getActionIndex();
                    int pointerId = pointerIdMap.get(motionEvent.getPointerId(i));
                    if (pointerId == 0) {
                        // Log.d("InGameEventProcessor", "Remove pointerId is 0");
                    } else {
                        pointerIdMap.delete(pointerId);
                        proxy.trySend(new RemovePointerMessage(pointerId));
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void clearPointer(LauncherSocketProxyClient proxy) {
        proxy.trySend(ClearPointerMessage.INSTANCE);
        pointerIdMap.clear();
    }

    public void clearPointer() {
        LauncherSocketProxyClient proxy = ControllerProxy.getProxyClient();
        if (proxy != null) {
            clearPointer(proxy);
        }
        pointerIdMap.clear();
    }

}
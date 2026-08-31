import { registerPlugin } from '@capacitor/core';

export type PickedAudio = {
  uri: string;
  name: string;
};

export interface AlarmSoundPlugin {
  pickAudio(): Promise<PickedAudio>;
  configureChannel(options: { soundUri?: string; channelId: string }): Promise<{ channelId: string }>;
}

export const NativeAlarmSound = registerPlugin<AlarmSoundPlugin>('AlarmSound');
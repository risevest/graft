import { WebPlugin } from '@capacitor/core';

import type {
  DeleteBundleOptions,
  DownloadBundleOptions,
  GetBlockedBundlesResult,
  GetChannelResult,
  GetCurrentBundleResult,
  GetDownloadedBundlesResult,
  GetInstallIdResult,
  GetNextBundleResult,
  GetVersionCodeResult,
  GetVersionNameResult,
  GraftPlugin,
  IsSyncingResult,
  ReadyResult,
  SetChannelOptions,
  SetNextBundleOptions,
  SyncResult,
} from './definitions';

export class GraftWeb extends WebPlugin implements GraftPlugin {
  public async clearBlockedBundles(): Promise<void> {
    this.throwUnimplementedError();
  }

  public async deleteBundle(_options: DeleteBundleOptions): Promise<void> {
    this.throwUnimplementedError();
  }

  public async downloadBundle(_options: DownloadBundleOptions): Promise<void> {
    this.throwUnimplementedError();
  }

  public async getBlockedBundles(): Promise<GetBlockedBundlesResult> {
    this.throwUnimplementedError();
  }

  public async getChannel(): Promise<GetChannelResult> {
    this.throwUnimplementedError();
  }

  public async getCurrentBundle(): Promise<GetCurrentBundleResult> {
    this.throwUnimplementedError();
  }

  public async getDownloadedBundles(): Promise<GetDownloadedBundlesResult> {
    this.throwUnimplementedError();
  }

  public async getInstallId(): Promise<GetInstallIdResult> {
    this.throwUnimplementedError();
  }

  public async getNextBundle(): Promise<GetNextBundleResult> {
    this.throwUnimplementedError();
  }

  public async getVersionCode(): Promise<GetVersionCodeResult> {
    this.throwUnimplementedError();
  }

  public async getVersionName(): Promise<GetVersionNameResult> {
    this.throwUnimplementedError();
  }

  public async isSyncing(): Promise<IsSyncingResult> {
    this.throwUnimplementedError();
  }

  public async ready(): Promise<ReadyResult> {
    this.throwUnimplementedError();
  }

  public async reload(): Promise<void> {
    this.throwUnimplementedError();
  }

  public async reset(): Promise<void> {
    this.throwUnimplementedError();
  }

  public async setChannel(_options: SetChannelOptions): Promise<void> {
    this.throwUnimplementedError();
  }

  public async setNextBundle(_options: SetNextBundleOptions): Promise<void> {
    this.throwUnimplementedError();
  }

  public async sync(): Promise<SyncResult> {
    this.throwUnimplementedError();
  }

  private throwUnimplementedError(): never {
    throw this.unimplemented('Not implemented on web.');
  }
}

package oyvindbs.zotshelf;

import android.content.Intent;
import android.widget.RemoteViewsService;

public class EpubCoversWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new EpubCoversRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}
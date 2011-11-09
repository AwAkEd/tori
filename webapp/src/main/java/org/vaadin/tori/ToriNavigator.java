package org.vaadin.tori;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import org.vaadin.tori.category.CategoryViewImpl;
import org.vaadin.tori.dashboard.DashboardViewImpl;
import org.vaadin.tori.mvp.NullViewImpl;
import org.vaadin.tori.mvp.View;
import org.vaadin.tori.thread.ThreadViewImpl;

import com.vaadin.Application;
import com.vaadin.terminal.ExternalResource;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.UriFragmentUtility;
import com.vaadin.ui.UriFragmentUtility.FragmentChangedEvent;
import com.vaadin.ui.UriFragmentUtility.FragmentChangedListener;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

@SuppressWarnings("serial")
public class ToriNavigator extends CustomComponent {

    /**
     * All the views of Tori application that can be navigated to.
     */
    public enum ApplicationView {
        // @formatter:off
        DASHBOARD("!dashboard", DashboardViewImpl.class),
        CATEGORIES("!category", CategoryViewImpl.class),
        THREADS("!thread", ThreadViewImpl.class),
        USERS("!user", NullViewImpl.class)
        ;
        // @formatter:on

        private String url;
        private Class<? extends org.vaadin.tori.mvp.View> viewClass;

        private ApplicationView(final String url,
                final Class<? extends org.vaadin.tori.mvp.View> viewClass) {
            this.url = url;
            this.viewClass = viewClass;
        }

        public String getUrl() {
            return url;
        }

        private static ApplicationView getDefault() {
            return DASHBOARD;
        }
    }

    private final HashMap<String, Class<? extends View>> uriToClass = new HashMap<String, Class<? extends View>>();
    private final HashMap<Class<? extends View>, String> classToUri = new HashMap<Class<? extends View>, String>();
    private final HashMap<Class<? extends View>, View> classToView = new HashMap<Class<? extends View>, View>();
    private String mainViewUri = null;
    private final VerticalLayout layout = new VerticalLayout();
    private final UriFragmentUtility uriFragmentUtil = new UriFragmentUtility();
    private String currentFragment = "";
    private View currentView = null;
    private final LinkedList<ViewChangeListener> listeners = new LinkedList<ViewChangeListener>();

    public ToriNavigator() {
        layout.setSizeFull();
        setSizeFull();
        layout.addComponent(uriFragmentUtil);
        setCompositionRoot(layout);
        uriFragmentUtil.addListener(new FragmentChangedListener() {
            @Override
            public void fragmentChanged(final FragmentChangedEvent source) {
                ToriNavigator.this.fragmentChanged();
            }
        });

        // Register all views of the application
        for (final ApplicationView appView : ApplicationView.values()) {
            addView(appView.getUrl(), appView.viewClass);
        }

        setMainView(ApplicationView.getDefault().getUrl());
    }

    private void fragmentChanged() {
        String newFragment = uriFragmentUtil.getFragment();
        if ("".equals(newFragment)) {
            newFragment = mainViewUri;
        }
        final int i = newFragment.indexOf('/');
        final String uri = i < 0 ? newFragment : newFragment.substring(0, i);
        final String requestedDataId = i < 0 || i + 1 == newFragment.length() ? null
                : newFragment.substring(i + 1);
        if (uriToClass.containsKey(uri)) {
            final View newView = getOrCreateView(uri);

            final String warn = currentView == null ? null : currentView
                    .getWarningForNavigatingFrom();
            if (warn != null && warn.length() > 0) {
                confirmedMoveToNewView(requestedDataId, newView, warn);
            } else {
                moveTo(newView, requestedDataId, false);
            }

        } else {
            uriFragmentUtil.setFragment(currentFragment, false);
        }
    }

    private void confirmedMoveToNewView(final String requestedDataId,
            final View newView, final String warn) {
        final VerticalLayout lo = new VerticalLayout();
        lo.setMargin(true);
        lo.setSpacing(true);
        lo.setWidth("400px");
        final Window wDialog = new Window("Warning", lo);
        wDialog.setModal(true);
        final Window main = getWindow();
        main.addWindow(wDialog);
        lo.addComponent(new Label(warn));
        lo.addComponent(new Label(
                "If you do not want to navigate away from the current screen, press Cancel."));
        final Button cancel = new Button("Cancel", new Button.ClickListener() {

            @Override
            public void buttonClick(final ClickEvent event) {
                uriFragmentUtil.setFragment(currentFragment, false);
                main.removeWindow(wDialog);
            }
        });
        final Button cont = new Button("Continue", new Button.ClickListener() {

            @Override
            public void buttonClick(final ClickEvent event) {
                main.removeWindow(wDialog);
                moveTo(newView, requestedDataId, false);
            }

        });
        final HorizontalLayout h = new HorizontalLayout();
        h.addComponent(cancel);
        h.addComponent(cont);
        h.setSpacing(true);
        lo.addComponent(h);
        lo.setComponentAlignment(h, Alignment.MIDDLE_RIGHT);
    }

    private View getOrCreateView(final String uri) {
        final Class<? extends View> newViewClass = uriToClass.get(uri);
        if (!classToView.containsKey(newViewClass)) {
            try {
                final View view = newViewClass.newInstance();
                view.init(this, getApplication());
                classToView.put(newViewClass, view);
            } catch (final InstantiationException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } catch (final IllegalAccessException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        final View v = classToView.get(newViewClass);
        return v;
    }

    private void moveTo(final View v, final String requestedDataId,
            final boolean noFragmentSetting) {
        currentFragment = classToUri.get(v.getClass());
        if (requestedDataId != null) {
            currentFragment += "/" + requestedDataId;
        }
        if (!noFragmentSetting
                && !currentFragment.equals(uriFragmentUtil.getFragment())) {
            uriFragmentUtil.setFragment(currentFragment, false);
        }
        Component removeMe = null;
        for (final Iterator<Component> i = layout.getComponentIterator(); i
                .hasNext();) {
            final Component c = i.next();
            if (c != uriFragmentUtil) {
                removeMe = c;
            }
        }
        if (removeMe != null) {
            layout.removeComponent(removeMe);
        }
        layout.addComponent(v);
        layout.setExpandRatio(v, 1.0F);
        v.navigateTo(requestedDataId);
        final View previousView = currentView;
        currentView = v;

        for (final ViewChangeListener l : listeners) {
            l.navigatorViewChange(previousView, currentView);
        }
    }

    /**
     * Get the main view.
     * 
     * Main view is the default view shown to user when he opens application
     * without specifying view uri.
     * 
     * @return Uri of the main view.
     */
    public String getMainView() {
        return mainViewUri;
    }

    /**
     * Set the main view.
     * 
     * Main view is the default view shown to user when he opens application
     * without specifying view uri. If main view has not been set, the first
     * view registered with addView() is used as main view. Note that the view
     * must be registered with addView() before calling this method.
     * 
     * @param mainViewUri
     *            Uri of the main view.
     */
    public void setMainView(final String mainViewUri) {
        if (uriToClass.containsKey(mainViewUri)) {
            this.mainViewUri = mainViewUri;
            if (currentView == null) {
                moveTo(getOrCreateView(mainViewUri), null, true);
            }
        } else {
            throw new IllegalArgumentException(
                    "No view with given uri can be found in the navigator");
        }
    }

    /**
     * Add a new view to navigator.
     * 
     * Register a view to navigator.
     * 
     * @param uri
     *            String that identifies a view. This is the string that is
     *            shown in URL after #
     * @param viewClass
     *            Component class that implements Navigator.View interface
     */
    public void addView(final String uri, final Class<? extends View> viewClass) {

        // Check parameters
        if (!View.class.isAssignableFrom(viewClass)) {
            throw new IllegalArgumentException(
                    "viewClass must implemenent Navigator.View");
        }

        if (uri == null || viewClass == null || uri.length() == 0) {
            throw new IllegalArgumentException(
                    "viewClass and uri must be non-null and not empty");
        }

        if (uriToClass.containsKey(uri)) {
            if (uriToClass.get(uri) == viewClass) {
                return;
            }

            throw new IllegalArgumentException(uriToClass.get(uri).getName()
                    + " is already mapped to '" + uri + "'");
        }

        if (classToUri.containsKey(viewClass)) {
            throw new IllegalArgumentException(
                    "Each view class can only be added to Navigator with one uri");
        }

        if (uri.indexOf('/') >= 0 || uri.indexOf('#') >= 0) {
            throw new IllegalArgumentException(
                    "Uri can not contain # or / characters");
        }

        uriToClass.put(uri, viewClass);
        classToUri.put(viewClass, uri);

        if (getMainView() == null) {
            setMainView(uri);
        }
    }

    /**
     * Remove view from navigator.
     * 
     * @param uri
     *            Uri of the view to remove.
     */
    public void removeView(final String uri) {
        final Class<? extends View> c = uriToClass.get(uri);
        if (c != null) {
            uriToClass.remove(uri);
            classToUri.remove(c);
            if (getMainView() == null || getMainView().equals(getMainView())) {
                if (uriToClass.size() == 0) {
                    mainViewUri = null;
                } else {
                    setMainView(uriToClass.keySet().iterator().next());
                }
            }
        }
    }

    /**
     * Get the uri for given view implementation class.
     * 
     * @param viewClass
     *            Class that implements the view.
     * @return Uri registered for the view class.
     */
    public String getUri(final Class<? extends View> viewClass) {
        return classToUri.get(viewClass);
    }

    /**
     * Get the view class for given uri.
     * 
     * @param uri
     *            Uri to get view for
     * @return View that corresponds to the uri
     */
    public Class<? extends View> getViewClass(final String uri) {
        return uriToClass.get(uri);
    }

    /**
     * Switch to view identified with uri.
     * 
     * Uri can be either the exact uri registered previously with addView() or
     * it can also contain data id passed to the view. In case data id is
     * included, the format is 'uri/freeFormedDataIdString'.
     * 
     * @param uri
     *            Uri where to navigate.
     */
    public void navigateTo(final String uri) {
        uriFragmentUtil.setFragment(uri);
    }

    /**
     * Switch to view implemented by given class.
     * 
     * Note that the view must be registered to navigator with addView() before
     * calling this method.
     * 
     * @param viewClass
     *            Class that implements the view.
     */
    public void navigateTo(final Class<? extends View> viewClass) {
        final String uri = getUri(viewClass);
        if (uri != null) {
            navigateTo(uri);
        }
    }

    /**
     * Listen to the view changes.
     * 
     * The listener will get notified after the view has changed.
     * 
     * @param listener
     *            Listener to invoke after view changes.
     */
    public void addListener(final ViewChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove the view change listener.
     * 
     * @param listener
     *            Listener to remove.
     */
    public void removeListener(final ViewChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Interface for listening to View changes.
     */
    public interface ViewChangeListener {

        /**
         * Invoked after the view has changed. Be careful for deadlocks if you
         * decide to change the view again in the listener.
         * 
         * @param previous
         *            Preview view before the change.
         * @param current
         *            New view after the change.
         */
        public void navigatorViewChange(View previous, View current);

    }

    /**
     * Interface implemented by all applications that uses Navigator.
     * 
     */
    public interface NavigableApplication {

        /**
         * Create a new browser window.
         * 
         * This method must construc a new window that could be used as a main
         * window for the application. Each call to this method must create a
         * new instance and your application should work when there are multiple
         * instances of concurrently. Each window can contain anything you like,
         * but at least they should contain a new Navigator instance for
         * controlling navigation within the window. Typically one also adds
         * somekind of menu for commanding navigator.
         * 
         * @return New window.
         */
        public Window createNewWindow();
    }

    /**
     * Helper for overriding Application.getWindow(String).
     * 
     * <p>
     * This helper makes implementing support for multiple browser tabs or
     * browser windows easy. Just override Application.getWindow(String) in your
     * application like this:
     * </p>
     * 
     * <pre>
     * &#064;Override
     * public Window getWindow(String name) {
     *     return Navigator.getWindow(this, name, super.getWindow(name));
     * }
     * </pre>
     * 
     * @param application
     *            Application instance, which implements
     *            Navigator.NavigableApplication interface.
     * @param name
     *            Name parameter from Application.getWindow(String name)
     * @param superGetWindow
     *            The window returned by super.getWindow(name)
     * @return
     * @throws IllegalArgumentException
     *             if <code>application</code> is not an instance of
     *             {@link Application}
     */
    public static Window getWindow(final NavigableApplication application,
            final String name, final Window superGetWindow) {
        if (superGetWindow != null) {
            return superGetWindow;
        }

        final Window w = application.createNewWindow();
        w.setName(name);

        if (application instanceof Application) {
            ((Application) application).addWindow(w);
        } else {
            throw new IllegalArgumentException(
                    "application must also be an instance of "
                            + Application.class.getName());
        }
        w.open(new ExternalResource(w.getURL()));
        return w;
    }

}

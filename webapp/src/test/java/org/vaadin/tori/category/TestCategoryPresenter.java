package org.vaadin.tori.category;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.vaadin.tori.data.DataSource;
import org.vaadin.tori.data.entity.Category;

public class TestCategoryPresenter {

    private CategoryPresenter presenter;

    private CategoryView mockView;
    private DataSource mockDataSource;

    @Before
    public void setup() {
        // create mocks
        mockView = mock(CategoryView.class);
        mockDataSource = mock(DataSource.class);

        // create the presenter to test
        presenter = new CategoryPresenter(mockDataSource);
        presenter.setView(mockView);
    }

    @Test
    public void invalidCategoryIdFormat() {
        presenter.setCurrentCategoryById("qwerty");
        verify(mockView).displayCategoryNotFoundError("qwerty");
    }

    @Test
    public void nonExistingCategoryId() {
        when(mockDataSource.getCategory(-1)).thenReturn(null);

        presenter.setCurrentCategoryById("-1");
        verify(mockView).displayCategoryNotFoundError("-1");
    }

    @Test
    public void existingCategoryId() {
        final Category category = new Category();
        when(mockDataSource.getCategory(1)).thenReturn(category);

        presenter.setCurrentCategoryById("1");
        assertEquals(category, presenter.getCurrentCategory());
    }
}

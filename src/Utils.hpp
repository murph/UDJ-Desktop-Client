/**
 * Copyright 2011 Kurtis L. Nusbaum
 * 
 * This file is part of UDJ.
 * 
 * UDJ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 * 
 * UDJ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with UDJ.  If not, see <http://www.gnu.org/licenses/>.
 */
#ifndef UTILS_HPP
#define UTILS_HPP
#include <QSqlQueryModel>
#include <QTableView>
#include <QSqlRecord>
#include <QSortFilterProxyModel>
#include <set>

namespace UDJ{


namespace Utils{

/**
 * Get's the ids currently selected in a view by 
 * getting them from the model (and or proxy model) backing the view.
 *
 * \param view The view whose selected ids are in question.
 * \param model The model contaning the ids.
 * \param colName The name of the id column in the model.
 * \param proxyModel A proxy model being used by the view.
 */
template<class T> QSet<T> getSelectedIds(
  const QTableView* view,
  const QSqlQueryModel* model,
  const QString& colName,
  const QSortFilterProxyModel *proxyModel=0)
{
  QModelIndexList selected = view->selectionModel()->selectedIndexes();
  QSet<T> selectedIds;
  std::set<int> rows;
  for(
    QModelIndexList::const_iterator it = selected.begin();
    it != selected.end();
    ++it
  )
  {
    rows.insert(
        (proxyModel ? proxyModel->mapToSource(*it).row() : it->row())
    );
  }
  for(
    std::set<int>::const_iterator it = rows.begin();
    it != rows.end();
    ++it
  )
  {
    QSqlRecord selectedRecord = model->record(*it);
    selectedIds.insert(
      selectedRecord.value(colName).value<T>());
  }
  return selectedIds;
}


} //end namespace utils


} //end namespae  UDJ
#endif //UTILS_HPP

/* fwHeaderGradient: thead.table-dark의 각 th에 서로 다른 gradient position을 줘서 '칸마다' 그라데이션처럼 보이게 */
(function fwHeaderGradient(){
  document.querySelectorAll('table.table thead.table-dark').forEach(function(thead){
    var ths = Array.prototype.slice.call(thead.querySelectorAll('th'));
    var n = ths.length;
    ths.forEach(function(th, i){
      var pos = (n <= 1) ? 0 : (i / (n - 1)) * 100;
      th.style.setProperty('--fw-bgpos', pos.toFixed(2) + '% 0%');
    });
  });
})();
